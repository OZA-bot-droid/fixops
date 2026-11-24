import axios from "axios";

const REFRESH_KEY = "fixops_demo_refresh";
const REFRESH_BUFFER_MS = 30_000;

// 일반 API Interceptor와 분리된 인증 전용 Axios.
// Refresh 요청이 다시 Refresh를 호출하는 재귀 문제를 방지한다.
export const authHttp = axios.create({
  baseURL: "/api",
  timeout: 10000,
});

const listeners = new Set();

let accessToken = null;
let expiresAt = 0;
let refreshPromise = null;
let loginPromise = null;
let logoutPromise = null;

// 이전 인증 세대에서 도착한 응답을 무효화하기 위한 번호.
let generation = 0;

let state = {
  user: null,
  status: sessionStorage.getItem(REFRESH_KEY) ? "restoring" : "anonymous",
  notice: null,
};

function publish(patch) {
  state = { ...state, ...patch };

  listeners.forEach((listener) => listener());
}

export function subscribe(listener) {
  listeners.add(listener);

  return () => listeners.delete(listener);
}

export function getSnapshot() {
  return state;
}

export function getAccessToken() {
  return accessToken;
}

export function getGeneration() {
  return generation;
}

export function getRefreshToken() {
  return sessionStorage.getItem(REFRESH_KEY);
}

export function isAccessTokenExpiring() {
  return !accessToken || Date.now() >= expiresAt - REFRESH_BUFFER_MS;
}

export function dismissNotice() {
  publish({ notice: null });
}

export function getAuthErrorCode(error) {
  return (
    error.response?.data?.error?.code ?? error.response?.data?.code ?? null
  );
}

// 인증 무효와 일시적인 서버 장애를 구분한다.
function isInvalidSession(error) {
  const status = error.response?.status;
  const code = getAuthErrorCode(error);

  return (
    status === 401 ||
    [
      "INVALID_REFRESH_TOKEN",
      "INVALID_SESSION",
      "SESSION_REVOKED",
      "SESSION_EXPIRED",
      "SESSION_NOT_ACTIVE",
    ].includes(code)
  );
}

function acceptTokens(data, expectedGeneration) {
  if (generation !== expectedGeneration) {
    throw new Error("AUTH_GENERATION_CHANGED");
  }

  const lifetime = Number(data.expiresIn);

  if (
    !data.accessToken ||
    !data.refreshToken ||
    !data.member ||
    !Number.isFinite(lifetime) ||
    lifetime <= 0
  ) {
    throw new Error("INVALID_AUTH_RESPONSE");
  }

  sessionStorage.setItem(REFRESH_KEY, data.refreshToken);

  accessToken = data.accessToken;

  expiresAt = Date.now() + lifetime * 1000;

  publish({
    user: data.member,
    status: logoutPromise ? "signingOut" : "authenticated",
    notice: null,
  });

  return data;
}

export function clearAuth(notice = null) {
  generation += 1;

  accessToken = null;
  expiresAt = 0;

  sessionStorage.removeItem(REFRESH_KEY);

  publish({
    user: null,
    status: "anonymous",
    notice,
  });
}

// 로그인
export function signIn(email, password) {
  if (loginPromise) {
    return loginPromise;
  }

  if (logoutPromise || refreshPromise || state.status === "restoring") {
    return Promise.reject(new Error("AUTH_REQUEST_IN_PROGRESS"));
  }

  if (state.status === "authenticated") {
    return Promise.reject(new Error("ALREADY_AUTHENTICATED"));
  }

  // 기존 인증 복원 실패 상태가 있다면
  // 신규 로그인으로 전환한다.
  clearAuth();

  const currentGeneration = generation;

  publish({
    status: "signingIn",
    notice: null,
  });

  loginPromise = authHttp
    .post("/members/login", {
      email,
      password,
    })
    .then((response) => {
      return acceptTokens(response.data.data, currentGeneration);
    })
    .catch((error) => {
      if (generation === currentGeneration) {
        clearAuth();
      }

      throw error;
    })
    .finally(() => {
      loginPromise = null;
    });

  return loginPromise;
}

// 내부 공통 Refresh 구현
function performRefresh(allowDuringLogout = false) {
  if (logoutPromise && !allowDuringLogout) {
    return Promise.reject(new Error("LOGOUT_IN_PROGRESS"));
  }

  // 진행 중인 Refresh가 있다면 동일한 Promise 사용.
  if (refreshPromise) {
    return refreshPromise;
  }

  const token = getRefreshToken();

  if (!token) {
    clearAuth();

    return Promise.reject(new Error("NO_REFRESH_TOKEN"));
  }

  const currentGeneration = generation;

  refreshPromise = authHttp
    .post("/members/refresh", {
      refreshToken: token,
    })
    .then((response) => {
      return acceptTokens(response.data.data, currentGeneration);
    })
    .catch((error) => {
      // 로그아웃이나 신규 인증으로 세대가 바뀌었다면
      // 이전 요청의 오류가 새 인증에 영향을 주지 않도록 한다.
      if (generation !== currentGeneration) {
        throw error;
      }

      if (isInvalidSession(error)) {
        clearAuth("세션이 만료되었거나 종료되었습니다. 다시 로그인해 주세요.");
      } else {
        // 5xx, Timeout, 네트워크 장애 등은
        // 세션 폐기가 확정된 것으로 판단하지 않는다.
        publish({
          status: logoutPromise
            ? "signingOut"
            : state.user
              ? "authenticated"
              : "unavailable",

          notice:
            "인증 서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.",
        });
      }

      throw error;
    })
    .finally(() => {
      refreshPromise = null;
    });

  return refreshPromise;
}

export function refreshAuth() {
  return performRefresh();
}

// 브라우저 새로고침 시 최초 인증 복원
export function restoreAuth() {
  if (state.status === "authenticated") {
    return Promise.resolve(state.user);
  }

  if (!getRefreshToken()) {
    clearAuth();

    return Promise.resolve(null);
  }

  if (refreshPromise) {
    return refreshPromise;
  }

  if (loginPromise || logoutPromise) {
    return Promise.reject(new Error("AUTH_REQUEST_IN_PROGRESS"));
  }

  publish({
    status: "restoring",
    notice: null,
  });

  return refreshAuth();
}

// 단일 로그아웃 및 전체 로그아웃
export function signOut(all = false) {
  if (logoutPromise) {
    return logoutPromise;
  }

  if (loginPromise) {
    return Promise.reject(new Error("LOGIN_IN_PROGRESS"));
  }

  // Promise를 먼저 등록하여 이후 Refresh 진입을 차단한다.
  logoutPromise = Promise.resolve()
    .then(async () => {
      publish({ status: "signingOut" });

      // 진행 중인 Refresh가 있다면 결과가 확정될 때까지
      // 기다린 후 최신 토큰을 사용한다.
      if (refreshPromise) {
        await refreshPromise;
      }

      // 현재 로그아웃 API는 Access Token도 요구한다.
      // 만료된 경우 먼저 토큰을 갱신한다.
      if (isAccessTokenExpiring()) {
        await performRefresh(true);
      }

      const headers = {
        Authorization: `Bearer ${accessToken}`,
      };

      if (all) {
        await authHttp.post("/members/logout-all", null, { headers });
      } else {
        await authHttp.post(
          "/members/logout",
          {
            refreshToken: getRefreshToken(),
          },
          { headers },
        );
      }

      clearAuth();

      return true;
    })
    .catch((error) => {
      const notice =
        "이 브라우저에서는 로그아웃되었습니다. " +
        "다만 서버 세션 종료는 확인하지 못했습니다.";

      clearAuth(notice);

      return false;
    })
    .finally(() => {
      logoutPromise = null;
    });

  return logoutPromise;
}
