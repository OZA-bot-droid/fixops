import { canSeeNavigation } from "../../features/member/onboardingPolicy";
import {OnboardingAccessProvider,useOnboardingAccess} from "../../features/member/OnboardingAccessProvider";
import { useState } from "react";
import { NavLink, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../../features/member/AuthProvider";
import { useOrganization } from "../../features/member/OrganizationProvider";
import OrganizationSelector from "./OrganizationSelector";
import ComplexSelector from "./ComplexSelector";
const nav = [
  { name: "대시보드", url: "/member", icon: "▦" },
  { name: "작업 관리", url: "/workorder", icon: "▤", roles: ["ADMIN", "TECHNICIAN", "USER"] },
  { name: "설비·위치", url: "/asset", icon: "◇", roles: ["ADMIN", "TECHNICIAN"] },
  { name: "예약 일정", url: "/booking", icon: "◷", roles: ["ADMIN", "TECHNICIAN", "USER"] },
  { name: "부품·재고", url: "/inventory", icon: "▣", roles: ["ADMIN", "TECHNICIAN"] },
  { name: "AI 점검센터", url: "/ai", icon: "✧", roles: ["ADMIN", "TECHNICIAN"] },
  { name: "서비스 도입 신청", url: "/organizations/apply", icon: "⚙", onboarding: true },
  { name: "조직 가입·초대", url: "/organizations/join", icon: "+", join: true },
  { name: "단지 등록·관리", url: "/complexes/manage", icon: "◇", buildingManagement: true },
  { name: "주민 등록·세대 연결", url: "/residents/enroll", icon: "⌂", resident: true },
  { name: "입주자 등록 코드", url: "/residents/registration-codes", icon: "♙", roles: ["ADMIN"], buildingOnly: true },
  { name: "주민 등록 심사", url: "/residents/review", icon: "♙", roles: ["ADMIN"], buildingOnly: true },
  { name: "입주자 명부", url: "/roster", icon: "▤", roles: ["ADMIN"], buildingOnly: true },
  { name: "단지 접근 권한", url: "/complexes/access", icon: "♙", roles: ["ADMIN"], buildingOnly: true },
  { name: "구성원 관리", url: "/members/manage", icon: "♙", roles: ["ADMIN"] },
  { name: "감사 로그", url: "/audit", icon: "≡", roles: ["ADMIN"] },
];
const titles = {
  "/member": "내 대시보드",
  "/organizations/apply": "서비스 도입 신청",
  "/organizations/join": "조직 가입·초대 수락",
  "/platform/organizations": "조직 승인 관리",
  "/members/manage": "회원·역할 관리",
  "/profile": "내 프로필·계정 설정",
  "/audit": "권한 변경 감사 로그",
  "/email-verification": "이메일 인증",
  "/complexes/register": "단지 등록·관리권 신청",
  "/complexes/manage": "단지 관리",
  "/complexes/access": "단지 접근 권한",
  "/roster": "입주자 명부 관리",
  "/residents/enroll": "주민 등록·세대 연결",
  "/residents/registration-codes": "입주자 등록 코드 관리",
  "/residents/review": "주민 등록 심사",
  "/platform/complex-managements": "플랫폼 단지 심사",
};
export default function AppLayout() {
  return <OnboardingAccessProvider><LayoutContent/></OnboardingAccessProvider>;
}
function LayoutContent() {
  const {applications}=useOnboardingAccess();
  const { user, signOut, signingOut } = useAuth();
  const loc = useLocation();
  const [open, setOpen] = useState(false);
  const title =
    titles[loc.pathname] ||
    nav.find((x) => loc.pathname.startsWith(x.url))?.name ||
    "FixOps";
  const { activeRole, activeOrganizationId, activeMembershipId, activeComplexId, activeOrganizationKind } = useOrganization();
  const visibleNav = nav.filter(item=>canSeeNavigation(user,item,{activeRole,activeOrganizationId,activeOrganizationKind},applications));
  return (
    <div className="min-h-screen bg-fixops-background text-fixops-text">
      <button
        className="fixed left-3 top-3 z-40 rounded-lg bg-fixops-sidebar px-3 py-2 text-white lg:hidden"
        onClick={() => setOpen(!open)}
        aria-label="메뉴 열기"
      >
        ☰
      </button>
      {open && (
        <button
          className="fixed inset-0 z-20 bg-black/30 lg:hidden"
          onClick={() => setOpen(false)}
          aria-label="메뉴 닫기"
        />
      )}
      <aside
        className={`fixed inset-y-0 left-0 z-30 flex w-[244px] flex-col bg-fixops-sidebar px-[14px] py-7 text-white transition-transform lg:translate-x-0 ${open ? "translate-x-0" : "-translate-x-full"}`}
      >
        <NavLink
          to="/member"
          onClick={() => setOpen(false)}
          className="px-4 text-[28px] font-bold tracking-tight"
        >
          ✦ FixOps
        </NavLink>
        <p className="mt-1 px-4 text-[9px] tracking-[.25em] text-[#91a1bc]">
          FACILITY OPERATIONS
        </p>
        <nav
          className="mt-8 flex min-h-0 flex-1 flex-col gap-[13px] overflow-y-auto"
          aria-label="주요 메뉴"
        >
          {visibleNav.map((item) => (
            <NavLink
              onClick={() => setOpen(false)}
              key={item.url}
              to={item.url}
              className={({ isActive }) =>
                `flex h-[46px] items-center gap-3 rounded-[9px] px-4 text-[15px] ${isActive ? "bg-fixops-sidebar-active font-semibold text-white" : "text-[#b2bed1] hover:bg-fixops-sidebar-active/60"}`
              }
            >
              <span aria-hidden className="w-6 text-xl text-[#a3b0c7]">
                {item.icon}
              </span>
              {item.name}
            </NavLink>
          ))}
          <div className="mt-3 border-t border-white/10 pt-3">
            <NavLink
              to="/profile"
              className="block rounded-lg px-4 py-3 text-sm text-[#b2bed1] hover:bg-fixops-sidebar-active"
            >
              내 프로필
            </NavLink>
            {user?.platformAdmin && (
              <>
              <NavLink
                to="/platform/organizations"
                className="block rounded-lg px-4 py-3 text-sm text-[#b2bed1] hover:bg-fixops-sidebar-active"
              >
                플랫폼 조직 개설 관리
              </NavLink>
              <NavLink to="/platform/complex-managements" className="block rounded-lg px-4 py-3 text-sm text-[#b2bed1] hover:bg-fixops-sidebar-active">
                플랫폼 단지 심사
              </NavLink>
              </>
            )}
          </div>
        </nav>
        <div className="rounded-xl bg-[#1e2c46] px-3 py-3">
          <p className="truncate text-[13px] font-semibold">
            {user?.name || "FixOps 사용자"}
          </p>
          <p className="mt-1 text-[11px] text-[#a8b6cc]">
            {activeRole ||
              (user?.platformAdmin ? "PLATFORM_ADMIN" : "조직 미소속")}
          </p>
          <button
            type="button"
            onClick={() => {
              signOut().catch(console.error);
            }}
            disabled={signingOut}
            className="
              mt-3 text-xs
              text-[#b2bed1] underline
              disabled:cursor-not-allowed
              disabled:opacity-50
            "
          >
            {signingOut ? "로그아웃 중…" : "로그아웃"}
          </button>
        </div>
      </aside>
      <div className="lg:pl-[244px]">
        <header className="flex min-h-[87px] flex-wrap items-center justify-between gap-3 py-3 border-b border-fixops-border bg-white px-6 pl-16 lg:px-11">
          <p className="text-sm text-fixops-muted">
            FixOps <span className="mx-2">/</span> {title}
          </p>
          <div className="flex flex-wrap items-center gap-3">
          {user?.platformAdmin !== true && <><OrganizationSelector/><ComplexSelector/></>}
          <span className="rounded-full bg-fixops-background px-5 py-2.5 text-[13px] font-semibold">
            {activeRole || (user?.platformAdmin ? "플랫폼 관리자" : "조직 미선택")}
          </span>
          </div>
        </header>
        <main className="mx-auto max-w-[1480px] px-5 py-10 lg:px-11">
          <Outlet key={`${activeOrganizationId ?? "none"}:${activeMembershipId ?? "none"}:${activeRole ?? "none"}:${activeComplexId ?? "none"}`} />
        </main>
      </div>
    </div>
  );
}
export function PageHeader({ title, description, action }) {
  return (
    <div className="mb-9 flex flex-wrap items-start justify-between gap-3">
      <div>
        <h1 className="text-[27px] font-bold leading-tight">{title}</h1>
        {description && (
          <p className="mt-3 text-[13px] text-fixops-muted">{description}</p>
        )}
      </div>
      {action}
    </div>
  );
}
export function ComingSoon({ name }) {
  return (
    <div className="rounded-2xl border border-fixops-border bg-white p-8">
      <h2 className="text-xl font-bold">{name}</h2>
      <p className="mt-2 text-fixops-muted">
        Figma 화면의 공동 디자인 시스템을 적용할 예정입니다. 데이터 및 API는
        담당 서비스 구현 후 연결됩니다.
      </p>
    </div>
  );
}
