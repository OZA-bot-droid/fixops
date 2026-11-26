import axios from "axios";
import {
  clearAuth, getAccessToken, getAuthErrorCode, getGeneration,
  getSnapshot, isAccessTokenExpiring, refreshAuth,
} from "./authSession";

const api = axios.create({ baseURL: "/api", timeout: 10000 });
const INVALID_SESSION_CODES = new Set([
  "SESSION_REVOKED", "SESSION_EXPIRED", "SESSION_NOT_ACTIVE", "INVALID_SESSION",
  "REFRESH_TOKEN_REUSED",
]);

// 변경 요청은 명시적으로 안전성이 보장된 API만 재시도한다.
function canRetryRequest(config) {
  const method = (config.method || "get").toLowerCase();
  return ["get", "head", "options"].includes(method) || config.retryAfterAuth === true;
}
function isCurrent(generation) {
  return generation === getGeneration() && getSnapshot().status === "authenticated";
}

api.interceptors.request.use(async (config) => {
  // api(originalRequest) 재시도에서도 최초 세대를 유지한다.
  const expected = config._authGeneration ?? getGeneration();
  if (!isCurrent(expected)) throw new Error("AUTH_STATE_CHANGED");
  if (isAccessTokenExpiring()) await refreshAuth();
  if (!isCurrent(expected)) throw new Error("AUTH_STATE_CHANGED");
  const token = getAccessToken();
  if (!token) throw new Error("ACCESS_TOKEN_MISSING");
  config.headers.Authorization = `Bearer ${token}`;
  config._authGeneration = expected;
  config._sentAccessToken = token;
  return config;
}, (error) => Promise.reject(error));

api.interceptors.response.use((response) => {
  if (!isCurrent(response.config._authGeneration)) {
    return Promise.reject(new Error("AUTH_STATE_CHANGED"));
  }
  return response;
}, async (error) => {
  const originalRequest = error.config;
  const status = error.response?.status;
  const code = getAuthErrorCode(error);
  if (!originalRequest || !error.response || !isCurrent(originalRequest._authGeneration)) {
    return Promise.reject(error);
  }
  // 인증 상태 무효와 업무 권한 부족, 서버 장애는 구분한다.
  if ([401, 403].includes(status) && INVALID_SESSION_CODES.has(code)) {
    clearAuth("로그인 세션이 종료되었습니다. 다시 로그인해 주세요.");
    return Promise.reject(error);
  }
  if (status === 403 || status >= 500) return Promise.reject(error);
  if (status === 401 && originalRequest._sentAccessToken && !originalRequest._retry &&
      canRetryRequest(originalRequest)) {
    originalRequest._retry = true;
    try {
      const latestToken = getAccessToken();
      if (!latestToken || latestToken === originalRequest._sentAccessToken || isAccessTokenExpiring()) {
        await refreshAuth();
      }
      if (!isCurrent(originalRequest._authGeneration)) return Promise.reject(error);
      originalRequest.headers.Authorization = `Bearer ${getAccessToken()}`;
      return api(originalRequest);
    } catch (refreshError) {
      return Promise.reject(refreshError);
    }
  }
  return Promise.reject(error);
});

export const unwrap = (response) => response.data.data;
export default api;
