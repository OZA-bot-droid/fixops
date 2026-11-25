import axios from "axios";

import {
  clearAuth,
  getAccessToken,
  getAuthErrorCode,
  getGeneration,
  getSnapshot,
  isAccessTokenExpiring,
  refreshAuth,
} from "./authSession";

const api = axios.create({
  baseURL: "/api",
  timeout: 10000,
});

const INVALID_SESSION_CODES = new Set([
  "SESSION_REVOKED",
  "SESSION_EXPIRED",
  "SESSION_NOT_ACTIVE",
  "INVALID_SESSION",
]);

// 조회 요청은 기본적으로 재시도 허용.
// 데이터 변경 요청은 인증 단계에서 거부되었음이
// 보장되는 API에 한하여 명시적으로 허용한다.
function canRetryRequest(config) {
  const method = (config.method || "get").toLowerCase();

  return (
    ["get", "head", "options"].includes(method) ||
    config.retryAfterAuth === true
  );
}

// ======================================================
// Request Interceptor
// ======================================================

api.interceptors.request.use(
  async (config) => {
    const currentGeneration = getGeneration();

    if (getSnapshot().status !== "authenticated") {
      throw new Error("AUTH_NOT_READY");
    }

    if (isAccessTokenExpiring()) {
      await refreshAuth();
    }

    if (
      currentGeneration !== getGeneration() ||
      getSnapshot().status !== "authenticated"
    ) {
      throw new Error("AUTH_STATE_CHANGED");
    }

    const token = getAccessToken();

    if (!token) {
      throw new Error("ACCESS_TOKEN_MISSING");
    }

    config.headers.Authorization = `Bearer ${token}`;

    config._authGeneration = currentGeneration;

    config._sentAccessToken = token;

    return config;
  },

  (error) => Promise.reject(error),
);

// ======================================================
// Response Interceptor
// ======================================================

api.interceptors.response.use(
  (response) => {
    if (
      response.config._authGeneration !== getGeneration() ||
      getSnapshot().status !== "authenticated"
    ) {
      return Promise.reject(new Error("AUTH_STATE_CHANGED"));
    }

    return response;
  },

  async (error) => {
    const originalRequest = error.config;
    const status = error.response?.status;
    const code = getAuthErrorCode(error);

    if (!originalRequest || !error.response) {
      return Promise.reject(error);
    }

    // 이전 세션에서 실행한 요청이
    // 현재 세션에 영향을 주지 않도록 한다.
    if (originalRequest._authGeneration !== getGeneration()) {
      return Promise.reject(error);
    }

    if (getSnapshot().status !== "authenticated") {
      return Promise.reject(error);
    }

    // 명시적으로 세션 무효가 확인된 경우.
    if ([401, 403].includes(status) && INVALID_SESSION_CODES.has(code)) {
      clearAuth("로그인 세션이 종료되었습니다. 다시 로그인해 주세요.");

      return Promise.reject(error);
    }

    // 일반적인 업무 권한 부족은
    // 인증 만료로 취급하지 않는다.
    if (status === 403) {
      return Promise.reject(error);
    }

    // 서버 장애는 로그아웃 사유가 아니다.
    if (status >= 500) {
      return Promise.reject(error);
    }

    // Access Token 인증 오류 처리
    if (
      status === 401 &&
      originalRequest._sentAccessToken &&
      !originalRequest._retry &&
      getSnapshot().status === "authenticated" &&
      canRetryRequest(originalRequest)
    ) {
      originalRequest._retry = true;

      try {
        const latestToken = getAccessToken();

        // 다른 요청에서 이미 Refresh를 완료했다면
        // 추가 Refresh 없이 최신 토큰을 사용한다.
        if (
          !latestToken ||
          latestToken === originalRequest._sentAccessToken ||
          isAccessTokenExpiring()
        ) {
          await refreshAuth();
        }

        if (
          originalRequest._authGeneration !== getGeneration() ||
          getSnapshot().status !== "authenticated"
        ) {
          return Promise.reject(error);
        }

        originalRequest.headers.Authorization = `Bearer ${getAccessToken()}`;

        return api(originalRequest);
      } catch (refreshError) {
        return Promise.reject(refreshError);
      }
    }

    // 재시도한 요청까지 실패하더라도
    // 다시 Refresh를 반복하지 않는다.
    return Promise.reject(error);
  },
);

export const unwrap = (response) => response.data.data;

export default api;
