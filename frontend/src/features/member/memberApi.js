import api, { unwrap } from "../../api/client";

import { authHttp, signIn, signOut, refreshAuth } from "../../api/authSession";

// ======================================================
// Authentication
// ======================================================

export const signup = async (data) =>
  unwrap(await authHttp.post("/members/signup", data));

// 로그인은 인증 세션 모듈을 통해 실행한다.
export const login = ({ email, password }) => signIn(email, password);

// Refresh Token은 authSession에서 직접 관리한다.
// 다른 화면에서 임의의 토큰을 전달하지 않는다.
export const refresh = () => refreshAuth();

// 단일 세션 로그아웃
export const logout = () => signOut(false);

// 전체 세션 로그아웃
export const logoutAll = () => signOut(true);

// ======================================================
// Member
// ======================================================

export const me = async () => unwrap(await api.get("/members/me"));

// ======================================================
// Organization
// ======================================================

export const applyOrg = async (data) =>
  unwrap(await api.post("/organizations", data));

export const myOrgs = async () => unwrap(await api.get("/organizations/mine"));

// ======================================================
// Platform Admin
// ======================================================

export const pendingOrgs = async () =>
  unwrap(await api.get("/platform/organizations/pending"));

export const decideOrg = async (id, approve, reason) =>
  unwrap(
    await api.patch(`/platform/organizations/${id}/decision`, {
      approve,
      reason,
    }),
  );
