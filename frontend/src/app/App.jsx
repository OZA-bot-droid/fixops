import { Navigate, Outlet, Route, Routes } from "react-router-dom";
import { AuthProvider, useAuth } from "../features/member/AuthProvider";
import { OrganizationProvider } from "../features/member/OrganizationProvider";
import RoleGuard from "../components/guards/RoleGuard";
import OnboardingGuard from "../components/guards/OnboardingGuard";
import OrganizationJoinPage from "../features/member/OrganizationJoinPage";
import MemberManagementPage from "../features/member/MemberManagementPage";
import LocationManagementPage from "../features/asset/LocationManagementPage";
import ResidentEnrollmentPage from "../features/member/ResidentEnrollmentPage";
import ResidentRegistrationCodePage from "../features/member/ResidentRegistrationCodePage";
import ResidentReviewPage from "../features/member/ResidentReviewPage";
import RosterManagementPage from "../features/member/RosterManagementPage";
import ComplexRegistrationPage from "../features/member/ComplexRegistrationPage";
import ComplexManagementPage from "../features/member/ComplexManagementPage";
import ComplexAccessPage from "../features/member/ComplexAccessPage";
import PlatformComplexReviewPage from "../features/member/PlatformComplexReviewPage";
import EmailVerificationPage from "../features/member/EmailVerificationPage";
import AppLayout, { ComingSoon } from "../components/layout/AppLayout";
import {
  LoginPage, SignupPage, PasswordResetPage, MemberHome, OrganizationApply,
  PlatformOrganizations, ProfilePage, UnavailablePage,
} from "../features/member/Pages";

function AuthLifecycleGate({ children }) {
  const {
    loading, signingOut, clearing, logoutFailed, unavailable,
    retryRestore, retryLogout, clearBrowserAuth,
  } = useAuth();

  if (loading || signingOut || clearing) {
    return (
      <div className="grid min-h-screen place-items-center text-fixops-muted">
        {signingOut ? "로그아웃 처리 중…" : clearing ? "인증 정보 초기화 중…" : "인증 상태 확인 중…"}
      </div>
    );
  }

  if (unavailable || logoutFailed) {
    return (
      <div className="grid min-h-screen place-items-center bg-fixops-background px-5">
        <div className="w-full max-w-md rounded-2xl border border-fixops-border bg-white p-8">
          <h2 className="text-xl font-bold">{logoutFailed ? "로그아웃 실패" : "인증 서버 연결 실패"}</h2>
          <p className="mt-3 text-sm text-fixops-muted">
            {logoutFailed
              ? "서버 세션 종료를 확인하지 못했습니다. 다시 시도해 주세요."
              : "인증 상태를 확인하지 못했습니다. 다시 시도하거나 로그인해 주세요."}
          </p>
          <div className="mt-6 flex flex-wrap gap-3">
            <button type="button"
              onClick={() => (logoutFailed ? retryLogout() : retryRestore()).catch(() => {})}
              className="rounded-lg bg-fixops-primary px-4 py-3 text-sm text-white">
              {logoutFailed ? "로그아웃 다시 시도" : "다시 시도"}
            </button>
            <button type="button"
              onClick={() => clearBrowserAuth().catch(() => {})}
              className="rounded-lg border border-fixops-border px-4 py-3 text-sm">
              {logoutFailed ? "종료 미확인 상태로 다시 로그인" : "다시 로그인"}
            </button>
          </div>
        </div>
      </div>
    );
  }

  // 공개 라우트도 포함하는 Gate에서는 user/platform 권한 검사를 하지 않는다.
  return children;
}

function Protected({ children, platform = false, verified = false }) {
  const { user, loggedIn } = useAuth();
  if (!loggedIn || !user) return <Navigate to="/login" replace />;
  if (platform && !user.platformAdmin) return <Navigate to="/member" replace />;
  if (verified && user.emailVerified !== true) return <Navigate to="/email-verification" replace />;
  return children;
}

function AuthNotice() {
  const { authNotice, dismissNotice } = useAuth();
  if (!authNotice) return null;
  return (
    <div className="fixed bottom-6 right-6 z-50 max-w-sm rounded-xl border border-amber-200 bg-amber-50 p-5 text-sm text-amber-900 shadow-lg" role="alert">
      <div className="flex items-start justify-between gap-4">
        <p>{authNotice}</p>
        <button type="button" onClick={dismissNotice} aria-label="알림 닫기">✕</button>
      </div>
    </div>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <OrganizationProvider>
      <AuthNotice />
      <AuthLifecycleGate>
        <Routes>
          <Route path="/" element={<Navigate to="/member" replace />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/signup" element={<SignupPage />} />
          <Route path="/forgot-password" element={<PasswordResetPage />} />
          <Route element={<Protected><AppLayout /></Protected>}>
            <Route path="/member" element={<MemberHome />} />
            <Route path="/profile" element={<ProfilePage />} />
            <Route path="/email-verification" element={<EmailVerificationPage />} />
            <Route element={<Protected verified><Outlet /></Protected>}>
            <Route element={<OnboardingGuard feature="apply"/>}><Route path="/organizations/apply" element={<OrganizationApply/>} /></Route>
            <Route element={<OnboardingGuard feature="join"/>}><Route path="/organizations/join" element={<OrganizationJoinPage/>} /></Route>
            <Route element={<OnboardingGuard feature="resident"/>}><Route path="/residents/enroll" element={<ResidentEnrollmentPage/>} /></Route>
            <Route element={<OnboardingGuard feature="building"/>}>
              <Route path="/complexes/register" element={<ComplexRegistrationPage/>}/>
              <Route path="/complexes/manage" element={<ComplexManagementPage/>}/>
            </Route>
            <Route element={<RoleGuard roles={["ADMIN"]} requireComplex buildingOnly />}>
              <Route path="/complexes/access" element={<ComplexAccessPage />} />
              <Route path="/roster" element={<RosterManagementPage />} />
              <Route path="/residents/registration-codes" element={<ResidentRegistrationCodePage />} />
              <Route path="/residents/review" element={<ResidentReviewPage />} />
            </Route>
            <Route element={<RoleGuard roles={["ADMIN"]} />}>
              <Route path="/members/manage" element={<MemberManagementPage />} />
              <Route path="/audit" element={<UnavailablePage title="권한 변경 감사 로그" />} />
            </Route>
            <Route element={<RoleGuard roles={["ADMIN", "TECHNICIAN"]} />}>
              <Route path="/inventory" element={<ComingSoon name="부품·재고" />} />
              <Route path="/ai" element={<ComingSoon name="AI 점검센터" />} />
            </Route>
            <Route element={<RoleGuard roles={["ADMIN", "TECHNICIAN"]} requireComplex />}>
              <Route path="/asset" element={<LocationManagementPage />} />
            </Route>
            <Route element={<RoleGuard roles={["ADMIN", "TECHNICIAN", "USER"]} requireComplex />}>
              <Route path="/workorder" element={<ComingSoon name="작업 관리" />} />
              <Route path="/booking" element={<ComingSoon name="예약 일정" />} />
            </Route>
            </Route>
          </Route>
          <Route element={<Protected platform><AppLayout /></Protected>}>
            <Route path="/platform/organizations" element={<PlatformOrganizations />} />
            <Route path="/platform/complex-managements" element={<PlatformComplexReviewPage />} />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </AuthLifecycleGate>
      </OrganizationProvider>
    </AuthProvider>
  );
}
