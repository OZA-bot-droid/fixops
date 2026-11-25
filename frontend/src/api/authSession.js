import axios from "axios";

const REFRESH_BUFFER_MS = 30_000;

// 일반 API Interceptor와 분리된 인증 전용 Axios.
// Refresh 요청이 다시 Refresh를 호출하는 재귀 문제를 방지한다.
export const authHttp = axios.create({
  baseURL: "/api",
  timeout: 10000,
  withCredentials: true,

  headers: {
    "X-FixOps-CSRF": "1",
  },
});

const listeners = new Set();

const AUTH_CHANNEL_NAME = "fixops-auth";

const authChannel =
  typeof BroadcastChannel !== "undefined"
    ? new BroadcastChannel(AUTH_CHANNEL_NAME)
    : null;

const AUTH_EVENT = Object.freeze({
  LOGIN: "LOGIN",
  LOGOUT: "LOGOUT",
  LOGOUT_ALL: "LOGOUT_ALL",
  COOKIE_CLEARED: "COOKIE_CLEARED",
});

const LOGOUT_PENDING_KEY = "fixops_logout_pending";

let accessToken = null;
let expiresAt = 0;
let refreshPromise = null;
let loginPromise = null;
let logoutPromise = null;
let clearCookiePromise = null;

// 이전 인증 세대에서 도착한 응답을 무효화하기 위한 번호.
let generation = 0;

let state = {
  user: null,
  sessionId: null,
  status: "restoring",
  notice: null,
  logoutAllRequested: false,
  logoutIntent: null,
};

function publish(patch) {
  state = { ...state, ...patch };

  listeners.forEach((listener) => listener());
}

function saveLogoutIntent(intent) {
  sessionStorage.setItem(LOGOUT_PENDING_KEY, JSON.stringify(intent));
}

function loadLogoutIntent() {
  try {
    const raw = sessionStorage.getItem(LOGOUT_PENDING_KEY);

    if (!raw) {
      return null;
    }

    const intent = JSON.parse(raw);

    if (
      intent.memberId == null ||
      typeof intent.sessionId !== "string" ||
      !intent.sessionId ||
      typeof intent.all !== "boolean"
    ) {
      return null;
    }

    return intent;
  } catch {
    return null;
  }
}

function removeLogoutIntent() {
  sessionStorage.removeItem(LOGOUT_PENDING_KEY);
}

function notifyAuthChange(type, sessionId = null, memberId = null) {
  try {
    authChannel?.postMessage({
      type,
      sessionId,
      memberId,
      timestamp: Date.now(),
    });
  } catch (error) {
    console.warn("인증 상태 동기화 실패", error);
  }
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
    !data.member ||
    data.member.id == null ||
    !data.sessionId ||
    !Number.isFinite(lifetime) ||
    lifetime <= 0
  ) {
    throw new Error("INVALID_AUTH_RESPONSE");
  }

  // 기존 회원 및 인증 세션과 일치하는지 확인
  if (
    state.sessionId &&
    (state.sessionId !== data.sessionId ||
      String(state.user?.id) !== String(data.member.id))
  ) {
    clearAuth(
      "다른 로그인 세션이 감지되었습니다. " + "인증을 다시 확인해 주세요.",
    );

    throw new Error("AUTH_SESSION_CHANGED");
  }

  accessToken = data.accessToken;
  expiresAt = Date.now() + lifetime * 1000;

  publish({
    user: data.member,
    sessionId: data.sessionId,
    status: logoutPromise ? "signingOut" : "authenticated",
    notice: null,
  });

  return data;
}

export function clearAuth(notice = null) {
  generation += 1;

  accessToken = null;
  expiresAt = 0;

  publish({
    user: null,
    sessionId: null,
    status: "anonymous",
    notice,
    logoutAllRequested: false,
  });
}

export function clearBrowserAuth(notice = null) {
  if (clearCookiePromise) {
    return clearCookiePromise;
  }

  if (loginPromise || refreshPromise || logoutPromise) {
    return Promise.reject(new Error("AUTH_REQUEST_IN_PROGRESS"));
  }

  const previousStatus = state.status;

  publish({
    status: "clearing",
  });

  clearCookiePromise = withAuthLock(async () => {
    const previousSessionId = state.sessionId;
    const previousMemberId = state.user?.id;

    await authHttp.post("/members/clear-cookie");

    notifyAuthChange(
      AUTH_EVENT.COOKIE_CLEARED,
      previousSessionId,
      previousMemberId,
    );

    clearAuth(notice);

    return true;
  })
    .catch(() => {
      publish({
        status: previousStatus,
        notice:
          "인증 쿠키를 초기화하지 못했습니다. " +
          "서버 연결 상태를 확인해 주세요.",
      });
      return false;
    })
    .finally(() => {
      clearCookiePromise = null;
    });

  return clearCookiePromise;
}

// 로그인
export function signIn(email, password) {
  if (clearCookiePromise) {
    return Promise.reject(new Error("COOKIE_CLEAR_IN_PROGRESS"));
  }

  if (loginPromise) {
    return loginPromise;
  }

  if (logoutPromise || refreshPromise || state.status === "restoring") {
    return Promise.reject(new Error("AUTH_REQUEST_IN_PROGRESS"));
  }

  if (state.status === "authenticated") {
    return Promise.reject(new Error("ALREADY_AUTHENTICATED"));
  }

  if (!["anonymous", "unavailable"].includes(state.status)) {
    return Promise.reject(new Error("AUTH_REQUEST_IN_PROGRESS"));
  }

  // 기존 인증 복원 실패 상태가 있다면
  // 신규 로그인으로 전환한다.
  clearAuth();

  const currentGeneration = generation;

  publish({
    status: "signingIn",
    notice: null,
  });

  loginPromise = withAuthLock(async () => {
    if (generation !== currentGeneration) {
      throw new Error("AUTH_GENERATION_CHANGED");
    }

    const response = await authHttp.post("/members/login", { email, password });

    const data = acceptTokens(response.data.data, currentGeneration);

    notifyAuthChange(AUTH_EVENT.LOGIN, data.sessionId, data.member.id);

    return data;
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

function withAuthLock(task) {
  if (navigator.locks?.request) {
    return navigator.locks.request(
      "fixops-auth-session",
      { mode: "exclusive" },
      task,
    );
  }

  return Promise.resolve().then(task);
}

async function refreshInsideLock(expectedGeneration) {
  if (generation !== expectedGeneration) {
    throw new Error("AUTH_GENERATION_CHANGED");
  }

  const response = await authHttp.post("/members/refresh");

  return acceptTokens(response.data.data, expectedGeneration);
}

export function retryLogout() {
  return signOut(state.logoutAllRequested);
}

// 내부 공통 Refresh 구현
function performRefresh() {
  if (clearCookiePromise) {
    return Promise.reject(new Error("COOKIE_CLEAR_IN_PROGRESS"));
  }

  if (logoutPromise) {
    return Promise.reject(new Error("LOGOUT_IN_PROGRESS"));
  }

  if (loginPromise) {
    return Promise.reject(new Error("LOGIN_IN_PROGRESS"));
  }

  if (refreshPromise) {
    return refreshPromise;
  }

  const currentGeneration = generation;

  refreshPromise = withAuthLock(() => refreshInsideLock(currentGeneration))
    .catch((error) => {
      if (generation !== currentGeneration) {
        throw error;
      }

      // 진행 중인 로그아웃 상태를 덮어쓰지 않는다.
      if (logoutPromise) {
        throw error;
      }

      const code = getAuthErrorCode(error);

      if (code === "REFRESH_COOKIE_MISSING") {
        clearAuth();
      } else if (isInvalidSession(error)) {
        clearAuth(
          "세션이 만료되었거나 종료되었습니다. " + "다시 로그인해 주세요.",
        );
      } else {
        publish({
          status: state.user ? "authenticated" : "unavailable",

          notice:
            "인증 서버에 연결하지 못했습니다. " + "잠시 후 다시 시도해 주세요.",
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
  if (["logoutFailed", "signingOut", "clearing"].includes(state.status)) {
    return Promise.reject(new Error("AUTH_REQUEST_IN_PROGRESS"));
  }

  return performRefresh();
}

// 브라우저 새로고침 시 최초 인증 복원
export function restoreAuth() {
  const pending = loadLogoutIntent();

  // ===== 신규 추가 =====

  if (pending) {
    accessToken = null;
    expiresAt = 0;

    publish({
      user: null,
      sessionId: null,

      status: "logoutRecovery",
      logoutIntent: pending,
      logoutAllRequested: pending.all,

      notice:
        "완료되지 않은 로그아웃 요청이 " +
        "있습니다. 로그아웃 상태를 " +
        "확인해 주세요.",
    });

    return Promise.resolve(null);
  }

  if (clearCookiePromise) {
    return Promise.reject(new Error("COOKIE_CLEAR_IN_PROGRESS"));
  }

  if (state.status === "authenticated") {
    return Promise.resolve(state.user);
  }

  if (refreshPromise) {
    return refreshPromise;
  }

  if (loginPromise || logoutPromise) {
    return Promise.reject(new Error("AUTH_REQUEST_IN_PROGRESS"));
  }

  if (state.status === "logoutFailed") {
    return Promise.reject(new Error("LOGOUT_RETRY_REQUIRED"));
  }

  publish({
    status: "restoring",
    notice: null,
  });

  return refreshAuth();
}

// 단일 로그아웃 및 전체 로그아웃
export function signOut(all = false) {
  if (clearCookiePromise) {
    return Promise.reject(new Error("COOKIE_CLEAR_IN_PROGRESS"));
  }

  if (logoutPromise) {
    return logoutPromise;
  }

  if (loginPromise) {
    return Promise.reject(new Error("LOGIN_IN_PROGRESS"));
  }

  // 로그아웃 요청 당시 인증 세션 보존
  const logoutIntent = {
    memberId: state.user?.id,
    sessionId: state.sessionId,
    all,
    requestedAt: Date.now(),
    generation,
  };

  if (logoutIntent.memberId == null || !logoutIntent.sessionId) {
    return Promise.reject(new Error("INVALID_SESSION"));
  }

  try {
    saveLogoutIntent({
      memberId: logoutIntent.memberId,
      sessionId: logoutIntent.sessionId,
      all: logoutIntent.all,
      requestedAt: logoutIntent.requestedAt,
    });
  } catch {
    return Promise.reject(new Error("LOGOUT_STATE_STORAGE_FAILED"));
  }

  function assertLogoutSession() {
    if (
      generation !== logoutIntent.generation ||
      String(state.user?.id) !== String(logoutIntent.memberId) ||
      state.sessionId !== logoutIntent.sessionId
    ) {
      throw new Error("AUTH_SESSION_CHANGED");
    }
  }

  logoutPromise = Promise.resolve()
    .then(async () => {
      publish({
        status: "signingOut",
        logoutAllRequested: all,
        logoutIntent,
      });

      // 1. 기존 Refresh 요청이 종료될 때까지 대기
      // Lock을 획득하기 전에 처리
      if (refreshPromise) {
        await refreshPromise;
      }

      assertLogoutSession();

      // 2. Logout 전용 Lock 획득
      return withAuthLock(async () => {
        assertLogoutSession();

        // 3. 필요할 경우 Lock 내부에서 직접 Refresh
        if (isAccessTokenExpiring()) {
          await refreshInsideLock(logoutIntent.generation);

          assertLogoutSession();
        }

        // 4. Refresh 이후에도 동일 회원/SID 검증
        assertLogoutSession();

        const headers = {
          Authorization: `Bearer ${accessToken}`,
        };

        // 5. 로그아웃 API 호출
        if (all) {
          await authHttp.post("/members/logout-all", null, { headers });
        } else {
          await authHttp.post("/members/logout", null, { headers });
        }

        // 6. 동일 세션인 경우 로컬 인증 상태 초기화
        assertLogoutSession();

        notifyAuthChange(
          all ? AUTH_EVENT.LOGOUT_ALL : AUTH_EVENT.LOGOUT,

          logoutIntent.sessionId,
          logoutIntent.memberId,
        );

        clearAuth();

        return true;
      });
    })
    .catch((error) => {
      if (
        error.message === "AUTH_SESSION_CHANGED" ||
        error.message === "AUTH_GENERATION_CHANGED" ||
        generation !== logoutIntent.generation
      ) {
        if (generation === logoutIntent.generation) {
          clearAuth(
            "로그인 세션이 변경되었습니다. " + "인증을 다시 확인해 주세요.",
          );
        }

        return false;
      }

      publish({
        status: "logoutFailed",
        logoutAllRequested: all,
        notice: "서버 로그아웃에 실패했습니다. " + "다시 시도해 주세요.",
      });

      return false;
    })
    .finally(() => {
      logoutPromise = null;
    });

  return logoutPromise;
}

export function recoverLogout() {
  if (logoutPromise) {
    return logoutPromise;
  }

  if (loginPromise || refreshPromise || clearCookiePromise) {
    return Promise.reject(new Error("AUTH_REQUEST_IN_PROGRESS"));
  }

  const pending = loadLogoutIntent();

  if (!pending) {
    return Promise.reject(new Error("LOGOUT_INTENT_NOT_FOUND"));
  }

  const currentGeneration = generation;

  logoutPromise = Promise.resolve()
    .then(() => {
      publish({
        status: "signingOut",
        logoutIntent: pending,
        logoutAllRequested: pending.all,
      });

      return withAuthLock(async () => {
        if (generation !== currentGeneration) {
          throw new Error("AUTH_GENERATION_CHANGED");
        }

        // 복구용 Refresh
        const response = await authHttp.post("/members/refresh");

        const data = response.data.data;

        if (generation !== currentGeneration) {
          throw new Error("AUTH_GENERATION_CHANGED");
        }

        // 최초 로그아웃 대상과 일치하는지 확인
        if (
          !data.sessionId ||
          data.sessionId !== pending.sessionId ||
          String(data.member?.id) !== String(pending.memberId)
        ) {
          throw new Error("AUTH_SESSION_CHANGED");
        }

        if (!data.accessToken) {
          throw new Error("INVALID_AUTH_RESPONSE");
        }

        // 업무 화면에 인증 상태를 공개하지 않고
        // 로그아웃 요청에서만 토큰 사용
        const headers = {
          Authorization: `Bearer ${data.accessToken}`,
        };

        const endpoint = pending.all
          ? "/members/logout-all"
          : "/members/logout";

        await authHttp.post(endpoint, null, { headers });

        removeLogoutIntent();

        notifyAuthChange(
          pending.all ? AUTH_EVENT.LOGOUT_ALL : AUTH_EVENT.LOGOUT,
          pending.sessionId,
          pending.memberId,
        );

        if (generation === currentGeneration) {
          clearAuth();
        }

        return true;
      });
    })
    .catch((error) => {
      if (generation !== currentGeneration) {
        return false;
      }

      const code = getAuthErrorCode(error);

      if (
        error.message === "AUTH_SESSION_CHANGED" ||
        error.message === "AUTH_GENERATION_CHANGED" ||
        code === "REFRESH_COOKIE_MISSING" ||
        isInvalidSession(error)
      ) {
        accessToken = null;
        expiresAt = 0;

        publish({
          user: null,
          sessionId: null,
          status: "logoutUncertain",
          logoutIntent: pending,

          notice:
            "기존 세션의 종료 여부를 확인할 수 " +
            "없습니다. 다른 세션은 종료하지 않습니다.",
        });

        return false;
      }

      publish({
        status: "logoutRecovery",
        logoutIntent: pending,

        notice:
          "로그아웃을 완료하지 못했습니다. " +
          "서버 연결 상태를 확인해 주세요.",
      });

      return false;
    })
    .finally(() => {
      logoutPromise = null;
    });

  return logoutPromise;
}

function handleAuthMessage(event) {
  const { type, sessionId, memberId } = event.data ?? {};

  switch (type) {
    case AUTH_EVENT.LOGIN: {
      // 현재 탭에서 인증 요청을 진행 중인 경우도 무효화
      if (
        (state.sessionId && state.sessionId !== sessionId) ||
        state.status === "restoring" ||
        state.status === "signingIn"
      ) {
        clearAuth("다른 탭에서 새로운 로그인이 " + "감지되었습니다.");
      }

      break;
    }

    case AUTH_EVENT.LOGOUT: {
      if (sessionId && state.sessionId === sessionId) {
        clearAuth("다른 탭에서 로그아웃되었습니다.");
      }

      break;
    }

    case AUTH_EVENT.LOGOUT_ALL: {
      if (
        memberId != null &&
        state.user?.id != null &&
        String(state.user.id) === String(memberId)
      ) {
        clearAuth("전체 기기에서 로그아웃되었습니다.");
      }

      break;
    }

    case AUTH_EVENT.COOKIE_CLEARED: {
      if (!sessionId || state.sessionId === sessionId) {
        clearAuth("다른 탭에서 인증 쿠키가 " + "초기화되었습니다.");
      }

      break;
    }

    default:
      break;
  }
  authChannel?.addEventListener("message", handleAuthMessage);
  if (import.meta.hot) {
    import.meta.hot.dispose(() => {
      authChannel?.removeEventListener("message", handleAuthMessage);

      authChannel?.close();
    });
  }
}
