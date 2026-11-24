import { useState } from "react";
import { NavLink, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../../features/member/AuthProvider";
import { Button } from "../ui";
const nav = [
  { name: "대시보드", url: "/member", icon: "▦" },
  { name: "작업 관리", url: "/workorder", icon: "▤" },
  { name: "설비·위치", url: "/asset", icon: "◇" },
  { name: "예약 일정", url: "/booking", icon: "◷" },
  { name: "부품·재고", url: "/inventory", icon: "▣" },
  { name: "AI 점검센터", url: "/ai", icon: "✧" },
  { name: "조직·설정", url: "/organizations/apply", icon: "⚙" },
];
const titles = {
  "/member": "내 대시보드",
  "/organizations/apply": "조직 생성·산업 선택",
  "/organizations/join": "조직 가입·초대 수락",
  "/platform/organizations": "조직 승인 관리",
  "/members/manage": "회원·역할 관리",
  "/profile": "내 프로필·계정 설정",
  "/audit": "권한 변경 감사 로그",
};
export default function AppLayout() {
  const { user, signOut, signingOut } = useAuth();
  const loc = useLocation();
  const [open, setOpen] = useState(false);
  const title =
    titles[loc.pathname] ||
    nav.find((x) => loc.pathname.startsWith(x.url))?.name ||
    "FixOps";
  const firstOrg = user?.memberships?.[0];
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
          className="mt-8 flex flex-1 flex-col gap-[13px]"
          aria-label="주요 메뉴"
        >
          {nav.map((item) => (
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
              <NavLink
                to="/platform/organizations"
                className="block rounded-lg px-4 py-3 text-sm text-[#b2bed1] hover:bg-fixops-sidebar-active"
              >
                플랫폼 조직 승인
              </NavLink>
            )}
          </div>
        </nav>
        <div className="rounded-xl bg-[#1e2c46] px-3 py-3">
          <p className="truncate text-[13px] font-semibold">
            {user?.name || "FixOps 사용자"}
          </p>
          <p className="mt-1 text-[11px] text-[#a8b6cc]">
            {firstOrg?.role ||
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
        <header className="flex min-h-[87px] items-center justify-between border-b border-fixops-border bg-white px-6 pl-16 lg:px-11">
          <p className="text-sm text-fixops-muted">
            FixOps <span className="mx-2">/</span> {title}
          </p>
          <span className="rounded-full bg-fixops-background px-5 py-2.5 text-[13px] font-semibold">
            {user?.platformAdmin ? "플랫폼 관리자" : firstOrg?.role || "사용자"}
          </span>
        </header>
        <main className="mx-auto max-w-[1480px] px-5 py-10 lg:px-11">
          <Outlet />
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
