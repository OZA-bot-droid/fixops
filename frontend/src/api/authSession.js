import axios from "axios";

const REFRESH_BUFFER_MS = 30_000;
const AUTH_LOCK = "fixops-auth-session";
const REAUTH_KEY = "fixops_reauth_required";
const LEGACY_PENDING_KEY = "fixops_logout_pending";
const AUTH_EVENT = Object.freeze({
  LOGIN: "LOGIN",
  LOGOUT: "LOGOUT",
  LOGOUT_ALL: "LOGOUT_ALL",
  COOKIE_CLEARED: "COOKIE_CLEARED",
});

// 인증 요청은 일반 API Interceptor를 사용하지 않는다.
export const authHttp = axios.create({
  baseURL: "/api",
  timeout: 10000,
  withCredentials: true,
  headers: {
    "Content-Type": "application/json",
    "X-FixOps-CSRF": "1",
  },
});

const listeners = new Set();
let authChannel = null;
try {
  if (typeof BroadcastChannel !== "undefined") {
    authChannel = new BroadcastChannel("fixops-auth");
  }
} catch {
  // BroadcastChannel이 불가능해도 서버 SID 검증은 유지한다.
}

let accessToken = null;
let expiresAt = 0;
let refreshPromise = null;
let loginPromise = null;
let logoutPromise = null;
let generation = 0;
let lastLocalMutation = 0;
let restoreBlock = null;

// 인증 비밀값은 저장하지 않는다. F5 후 자동 복원만 차단하는 표시다.
// 이전 버전의 복구 의도는 자동 재시도하지 않고 재로그인으로 전환한다.
try {
  restoreBlock = sessionStorage.getItem(REAUTH_KEY);
  if (sessionStorage.getItem(LEGACY_PENDING_KEY)) {
    restoreBlock = "logout-pending";
    sessionStorage.setItem(REAUTH_KEY, restoreBlock);
    sessionStorage.removeItem(LEGACY_PENDING_KEY);
  }
} catch {
  // Storage 제한 환경에서는 현재 페이지 메모리 범위에서만 차단된다.
}

let state = {
  user: null,
  sessionId: null,
  status: "restoring",
  notice: null,
  logoutAllRequested: false,
};

function publish(patch) {
  state = { ...state, ...patch };
  listeners.forEach((listener) => listener());
}

function setRestoreBlock(value) {
  restoreBlock = value;
  try {
    if (value) sessionStorage.setItem(REAUTH_KEY, value);
    else sessionStorage.removeItem(REAUTH_KEY);
    sessionStorage.removeItem(LEGACY_PENDING_KEY);
  } catch {
    // Storage 실패가 서버 로그아웃 자체를 막으면 안 된다.
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
  // 최종 세션 수명이 짧은 경우에도 매 요청마다 회전하지 않도록
  // 발급 때 결정한 갱신 시점을 이용한다.
  return !accessToken || Date.now() >= refreshAt;
}
let refreshAt = 0;
export function dismissNotice() {
  publish({ notice: null });
}
export function getAuthErrorCode(error) {
  return (
    error?.response?.data?.error?.code ?? error?.response?.data?.code ?? null
  );
}
function isInvalidSession(error) {
  const code = getAuthErrorCode(error);
  return (
    error?.response?.status === 401 ||
    [
      "INVALID_REFRESH_TOKEN",
      "INVALID_SESSION",
      "SESSION_REVOKED",
      "SESSION_EXPIRED",
      "SESSION_NOT_ACTIVE",
      "REFRESH_TOKEN_REUSED",
      "SESSION_SCOPE_DENIED",
    ].includes(code)
  );
}
function assertGeneration(expected) {
  if (generation !== expected) throw new Error("AUTH_GENERATION_CHANGED");
}
function currentIdentity() {
  return state.sessionId && state.user?.id != null
    ? { sessionId: state.sessionId, memberId: state.user.id }
    : null;
}
function matchesIdentity(data, identity) {
  return (
    !identity ||
    (data?.sessionId === identity.sessionId &&
      String(data?.member?.id) === String(identity.memberId))
  );
}

function acceptTokens(data, expectedGeneration, expectedIdentity = null) {
  assertGeneration(expectedGeneration);
  const lifetime = Number(data?.expiresIn);
  if (
    !data?.accessToken ||
    !data?.sessionId ||
    data?.member?.id == null ||
    !Number.isFinite(lifetime) ||
    lifetime <= 0
  ) {
    throw new Error("INVALID_AUTH_RESPONSE");
  }
  if (!matchesIdentity(data, expectedIdentity ?? currentIdentity())) {
    clearAuth("다른 로그인 세션이 감지되었습니다. 다시 로그인해 주세요.");
    throw new Error("AUTH_SESSION_CHANGED");
  }
  accessToken = data.accessToken;
  const lifetimeMs = lifetime * 1000;
  expiresAt = Date.now() + lifetimeMs;
  refreshAt = expiresAt - Math.min(REFRESH_BUFFER_MS, lifetimeMs / 5);
  publish({
    user: data.member,
    sessionId: data.sessionId,
    status: logoutPromise ? "signingOut" : "authenticated",
    notice: null,
  });
  return data;
}

// 로컬 인증만 폐기한다. 다른 탭의 공유 Cookie를 삭제하지 않는다.
export function clearAuth(notice = null) {
  generation += 1;
  accessToken = null;
  expiresAt = 0;
  refreshAt = 0;
  setRestoreBlock(restoreBlock === "logout-pending" ? restoreBlock : "reauth");
  publish({
    user: null,
    sessionId: null,
    status: "anonymous",
    notice,
    logoutAllRequested: false,
  });
}

// 기존 Context API 이름 유지. 실제 서버 로그아웃 또는 Cookie 삭제가 아니다.
// 사용자 명시 로그인으로 Cookie를 교체하도록 하여 다른 탭의 Cookie 삭제를 방지한다.
export function clearBrowserAuth(notice = null) {
  if (loginPromise || refreshPromise || logoutPromise) {
    return Promise.reject(new Error("AUTH_REQUEST_IN_PROGRESS"));
  }
  const uncertain =
    restoreBlock === "logout-pending" || state.status === "logoutFailed";
  clearAuth(
    notice ??
      (uncertain
        ? "이전 서버 세션의 종료 여부를 확인하지 못했습니다. 다시 로그인해 주세요."
        : "다시 로그인해 주세요."),
  );
  return Promise.resolve(true);
}

function withAuthLock(task) {
  if (globalThis.navigator?.locks?.request) {
    return globalThis.navigator.locks.request(
      AUTH_LOCK,
      { mode: "exclusive" },
      task,
    );
  }
  // HTTPS/localhost가 아닌 환경에서는 탭 간 직렬화가 보장되지 않는다.
  return Promise.resolve().then(task);
}
function notifyAuthChange(type, identity) {
  lastLocalMutation = Date.now();
  try {
    authChannel?.postMessage({
      type,
      ...identity,
      timestamp: lastLocalMutation,
    });
  } catch {
    // 수신 누락은 서버의 회원/SID 검증으로 보완한다.
  }
}

export function signIn(email, password) {
  if (loginPromise) return loginPromise;
  if (
    logoutPromise ||
    refreshPromise ||
    !["anonymous", "unavailable"].includes(state.status)
  ) {
    return Promise.reject(new Error("AUTH_REQUEST_IN_PROGRESS"));
  }
  clearAuth();
  const expectedGeneration = generation;
  publish({ status: "signingIn", notice: null });
  loginPromise = withAuthLock(async () => {
    assertGeneration(expectedGeneration);
    const response = await authHttp.post("/members/login", { email, password });
    const data = acceptTokens(response.data.data, expectedGeneration);
    setRestoreBlock(null);
    notifyAuthChange(AUTH_EVENT.LOGIN, currentIdentity());
    return data;
  })
    .catch((error) => {
      if (generation === expectedGeneration) clearAuth();
      throw error;
    })
    .finally(() => {
      loginPromise = null;
    });
  return loginPromise;
}

// 호출자는 Lock을 이미 획득해야 한다. 기대 SID를 서버에도 보내서
// 다른 탭의 Cookie를 회전시키기 전에 불일치를 차단한다.
async function refreshInsideLock(expectedGeneration, identity) {
  assertGeneration(expectedGeneration);
  const response = await authHttp.post("/members/refresh", identity ?? {}, {
    headers: {
      "Content-Type": "application/json",
    },
  });
  return acceptTokens(response.data.data, expectedGeneration, identity);
}

function performRefresh() {
  if (refreshPromise) return refreshPromise;
  if (logoutPromise || loginPromise) {
    return Promise.reject(new Error("AUTH_REQUEST_IN_PROGRESS"));
  }
  const expectedGeneration = generation;
  const identity = currentIdentity();
  refreshPromise = withAuthLock(() =>
    refreshInsideLock(expectedGeneration, identity),
  )
    .catch((error) => {
      if (generation !== expectedGeneration || logoutPromise) throw error;
      const code = getAuthErrorCode(error);
      if (code === "REFRESH_COOKIE_MISSING") {
        // 최초 방문/쿠키 없음에는 만료 안내를 표시하지 않는다.
        if (identity)
          clearAuth("로그인 세션이 종료되었습니다. 다시 로그인해 주세요.");
        else clearAuth();
      } else if (isInvalidSession(error)) {
        clearAuth("세션이 만료되었거나 종료되었습니다. 다시 로그인해 주세요.");
      } else {
        publish({
          status: state.user ? "authenticated" : "unavailable",
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
  if (restoreBlock || !["authenticated", "restoring"].includes(state.status)) {
    return Promise.reject(new Error("AUTH_REQUEST_IN_PROGRESS"));
  }
  return performRefresh();
}

export function restoreAuth() {
  if (loginPromise || logoutPromise || state.status === "logoutFailed") {
    return Promise.reject(new Error("AUTH_REQUEST_IN_PROGRESS"));
  }
  if (state.status === "authenticated") return Promise.resolve(state.user);
  if (refreshPromise) return refreshPromise;
  if (restoreBlock) {
    clearAuth(
      restoreBlock === "logout-pending"
        ? "이전 서버 로그아웃의 완료 여부를 확인하지 못했습니다. 다시 로그인해 주세요."
        : null,
    );
    return Promise.resolve(null);
  }
  publish({ status: "restoring", notice: null });
  return refreshAuth();
}

export function signOut(all = false) {
  if (logoutPromise) return logoutPromise;
  if (
    loginPromise ||
    !["authenticated", "logoutFailed"].includes(state.status)
  ) {
    return Promise.reject(new Error("AUTH_REQUEST_IN_PROGRESS"));
  }
  const identity = currentIdentity();
  if (!identity) return Promise.reject(new Error("INVALID_SESSION"));
  const expectedGeneration = generation;
  // F5 직후 일반 Refresh를 통한 업무 화면 복원을 막는다.
  setRestoreBlock("logout-pending");
  const assertLogoutSession = () => {
    assertGeneration(expectedGeneration);
    if (
      !matchesIdentity(
        { sessionId: state.sessionId, member: state.user },
        identity,
      )
    ) {
      throw new Error("AUTH_SESSION_CHANGED");
    }
  };
  logoutPromise = Promise.resolve()
    .then(async () => {
      // 이미 시작한 갱신이 끝나기를 Lock 바깥에서 기다린다.
      if (refreshPromise) await refreshPromise;
      assertLogoutSession();
      return withAuthLock(async () => {
        assertLogoutSession();
        if (isAccessTokenExpiring()) {
          await refreshInsideLock(expectedGeneration, identity);
        }
        assertLogoutSession();
        const endpoint = all ? "/members/logout-all" : "/members/logout";
        // 두 API 모두 서버에서 Bearer의 회원/SID와 Cookie 소유권을 대조한다.
        await authHttp.post(endpoint, null, {
          headers: { Authorization: `Bearer ${accessToken}` },
        });
        // 서버 성공은 다른 탭에도 전달한다. 로컬 상태가 바뀌었으면 덮어쓰지 않는다.
        notifyAuthChange(
          all ? AUTH_EVENT.LOGOUT_ALL : AUTH_EVENT.LOGOUT,
          identity,
        );
        if (generation === expectedGeneration) {
          setRestoreBlock("reauth");
          clearAuth();
        }
        return true;
      });
    })
    .catch((error) => {
      if (generation !== expectedGeneration) return false;
      if (
        isInvalidSession(error) ||
        ["AUTH_SESSION_CHANGED", "AUTH_GENERATION_CHANGED"].includes(
          error.message,
        )
      ) {
        clearAuth(
          "이전 세션의 종료 여부를 확인할 수 없습니다. 다른 세션은 종료하지 않습니다. 다시 로그인해 주세요.",
        );
        return false;
      }
      publish({
        status: "logoutFailed",
        logoutAllRequested: all,
        notice: "서버 로그아웃에 실패했습니다. 다시 시도해 주세요.",
      });
      return false;
    })
    .finally(() => {
      logoutPromise = null;
    });
  // 업무 요청을 즉시 차단한다. 이미 처리된 서버 요청은 취소하지 않는다.
  publish({ status: "signingOut", logoutAllRequested: all });
  return logoutPromise;
}

export function retryLogout() {
  if (state.status !== "logoutFailed" || !currentIdentity()) {
    return Promise.reject(new Error("LOGOUT_RETRY_REQUIRED"));
  }
  return signOut(state.logoutAllRequested);
}

function handleAuthMessage(event) {
  const { type, sessionId, memberId, timestamp } = event.data ?? {};
  if (!Number.isFinite(timestamp) || timestamp < lastLocalMutation) return;
  const restoring = state.status === "restoring";
  const matchingSid = Boolean(sessionId && state.sessionId === sessionId);
  const matchingMember =
    memberId != null &&
    state.user?.id != null &&
    String(state.user.id) === String(memberId);
  if (
    type === AUTH_EVENT.LOGIN &&
    ((state.sessionId && !matchingSid) ||
      restoring ||
      state.status === "signingIn")
  ) {
    clearAuth(
      "다른 탭에서 새로운 로그인이 감지되었습니다. 다시 로그인해 주세요.",
    );
  } else if (
    (type === AUTH_EVENT.LOGOUT && (matchingSid || restoring)) ||
    (type === AUTH_EVENT.LOGOUT_ALL && (matchingMember || restoring))
  ) {
    // 단일 로그아웃 이벤트가 전체 로그아웃 성공을 증명하는 것은 아니다.
    if (
      restoreBlock !== "logout-pending" ||
      (matchingSid && !state.logoutAllRequested) ||
      (type === AUTH_EVENT.LOGOUT_ALL && matchingMember)
    ) {
      setRestoreBlock("reauth");
    }
    clearAuth(
      type === AUTH_EVENT.LOGOUT_ALL
        ? "전체 기기에서 로그아웃되었습니다."
        : "다른 탭에서 로그아웃되었습니다.",
    );
  } else if (
    type === AUTH_EVENT.COOKIE_CLEARED &&
    (!sessionId || matchingSid || restoring)
  ) {
    clearAuth("다른 탭에서 인증 쿠키가 초기화되었습니다.");
  }
}

// 반드시 수신 함수 바깥에서 최초 1회 등록한다.
authChannel?.addEventListener("message", handleAuthMessage);
if (import.meta.hot) {
  import.meta.hot.dispose(() => {
    authChannel?.removeEventListener("message", handleAuthMessage);
    authChannel?.close();
  });
}
