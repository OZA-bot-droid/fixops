import { Navigate, Route, Routes } from "react-router-dom";
import { AuthProvider, useAuth } from "../features/member/AuthProvider";
import AppLayout, { ComingSoon } from "../components/layout/AppLayout";
import {
  LoginPage,
  SignupPage,
  PasswordResetPage,
  MemberHome,
  OrganizationApply,
  PlatformOrganizations,
  ProfilePage,
  UnavailablePage,
} from "../features/member/Pages";

function Protected({ children, platform = false }) {
  const {
    user,
    loading,
    signingOut,
    clearing,
    logoutFailed,
    unavailable,
    retryRestore,
    retryLogout,
    clearBrowserAuth,
  } = useAuth();

  if (loading || signingOut || clearing) {
    return (
      <div
        className="
        grid min-h-screen
        place-items-center
        text-fixops-muted
      "
      >
        {signingOut
          ? "로그아웃 처리 중…"
          : clearing
            ? "인증 정보 초기화 중…"
            : "인증 상태 확인 중…"}
      </div>
    );
  }

  if (unavailable) {
    return (
      <div
        className="
        grid min-h-screen
        place-items-center
        bg-fixops-background
        px-5
      "
      >
        <div
          className="
          w-full max-w-md
          rounded-2xl
          border border-fixops-border
          bg-white p-8
        "
        >
          <h2 className="text-xl font-bold">인증 서버 연결 실패</h2>
          <p
            className="
            mt-3 text-sm
            text-fixops-muted
          "
          >
            인증 상태를 확인하지 못했습니다. 다시 시도하거나 로그인해 주세요.
          </p>

          <div className="mt-6 flex gap-3">
            <button
              onClick={() => retryRestore().catch(() => {})}
              className="
                rounded-lg
                bg-fixops-primary
                px-4 py-3
                text-sm text-white
              "
            >
              다시 시도
            </button>
            <button
              onClick={() => {
                clearBrowserAuth().catch(() => {});
              }}
              className="
                rounded-lg
                border border-fixops-border
                px-4 py-3 text-sm
              "
            >
              다시 로그인
            </button>
          </div>
        </div>
      </div>
    );
  }

  if (logoutFailed) {
    return (
      <div
        className="
      grid min-h-screen
      place-items-center
      bg-fixops-background px-5
    "
      >
        <div
          className="
        w-full max-w-md
        rounded-2xl
        border border-fixops-border
        bg-white p-8
      "
        >
          <h2 className="text-xl font-bold">로그아웃 실패</h2>

          <p
            className="
          mt-3 text-sm
          text-fixops-muted
        "
          >
            서버 세션 종료를 확인하지 못했습니다. 다시 시도해 주세요.
          </p>

          <button
            type="button"
            onClick={() => {
              retryLogout().catch(() => {});
            }}
            className="
            mt-6 rounded-lg
            bg-fixops-primary
            px-4 py-3 text-sm text-white
          "
          >
            로그아웃 다시 시도
          </button>
        </div>
      </div>
    );
  }

  if (!user) {
    return <Navigate to="/login" replace />;
  }

  if (platform && !user.platformAdmin) {
    return <Navigate to="/member" replace />;
  }

  return children;
}

function AuthNotice() {
  const { authNotice, dismissNotice } = useAuth();

  if (!authNotice) {
    return null;
  }

  return (
    <div
      className="
      fixed bottom-6 right-6 z-50
      max-w-sm rounded-xl
      border border-amber-200
      bg-amber-50 p-5
      text-sm text-amber-900
      shadow-lg
    "
      role="alert"
    >
      <div
        className="
        flex items-start
        justify-between gap-4
      "
      >
        <p>{authNotice}</p>

        <button onClick={dismissNotice} aria-label="알림 닫기">
          ✕
        </button>
      </div>
    </div>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <AuthNotice />
      <Routes>
        <Route path="/" element={<Navigate to="/member" replace />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/signup" element={<SignupPage />} />
        <Route path="/forgot-password" element={<PasswordResetPage />} />
        <Route
          element={
            <Protected>
              <AppLayout />
            </Protected>
          }
        >
          <Route path="/member" element={<MemberHome />} />
          <Route path="/profile" element={<ProfilePage />} />
          <Route path="/organizations/apply" element={<OrganizationApply />} />
          <Route
            path="/organizations/join"
            element={<UnavailablePage title="조직 가입·초대 수락" />}
          />
          <Route
            path="/members/manage"
            element={<UnavailablePage title="회원·역할 관리" />}
          />
          <Route
            path="/audit"
            element={<UnavailablePage title="권한 변경 감사 로그" />}
          />
          <Route path="/asset" element={<ComingSoon name="설비·위치" />} />
          <Route path="/workorder" element={<ComingSoon name="작업 관리" />} />
          <Route path="/booking" element={<ComingSoon name="예약 일정" />} />
          <Route path="/inventory" element={<ComingSoon name="부품·재고" />} />
          <Route path="/ai" element={<ComingSoon name="AI 점검센터" />} />
        </Route>
        <Route
          element={
            <Protected platform>
              <AppLayout />
            </Protected>
          }
        >
          <Route
            path="/platform/organizations"
            element={<PlatformOrganizations />}
          />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AuthProvider>
  );
}
