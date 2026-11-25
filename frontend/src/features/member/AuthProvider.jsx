import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useSyncExternalStore,
} from "react";

import {
  subscribe,
  getSnapshot,
  restoreAuth,
  signIn,
  signOut,
  refreshAuth,
  clearAuth,
  clearBrowserAuth,
  retryLogout,
  dismissNotice,
} from "../../api/authSession";

const Context = createContext(null);

export function AuthProvider({ children }) {
  const auth = useSyncExternalStore(subscribe, getSnapshot);

  // 앱 최초 진입 시 인증 복원.
  // StrictMode에서 Effect가 중복 실행되어도
  // authSession의 refreshPromise가 요청을 공유한다.
  useEffect(() => {
    restoreAuth().catch(() => {
      // 오류 상태는 authSession에서 관리한다.
      // 여기서 다시 clearAuth를 호출하지 않는다.
    });
  }, []);

  const value = useMemo(
    () => ({
      user: auth.user,
      status: auth.status,
      loading: auth.status === "restoring",
      loggedIn: Boolean(auth.user),
      signingIn: auth.status === "signingIn",
      signingOut: auth.status === "signingOut",
      unavailable: auth.status === "unavailable",
      authNotice: auth.notice,

      signIn,
      signOut: () => signOut(false),
      signOutAll: () => signOut(true),
      refreshAuth,
      retryRestore: restoreAuth,
      retryLogout,
      clearAuth,
      clearBrowserAuth,
      dismissNotice,
    }),
    [auth],
  );

  return <Context.Provider value={value}>{children}</Context.Provider>;
}

export const useAuth = () => {
  const value = useContext(Context);

  if (!value) {
    throw new Error("AuthProvider required");
  }

  return value;
};
