import { useState } from "react";
import { PageHeader } from "../../components/layout/AppLayout";
import { useOrganization } from "../member/OrganizationProvider";
import { Tabs } from "./inventoryUi";
import { setInventoryScope } from "./inventoryApi";
import PartListTab from "./tabs/PartListTab";
import PartReceiveTab from "./tabs/PartReceiveTab";
import UsageByWorkOrderTab from "./tabs/UsageByWorkOrderTab";
import StockHistoryTab from "./tabs/StockHistoryTab";
import PartUseTab from "./tabs/PartUseTab";
import PartRequestTab from "./tabs/PartRequestTab";
import PartRequestAdminTab from "./tabs/PartRequestAdminTab";

/**
 * 부품·재고 (/inventory) — 하위 메뉴 대신 한 페이지 안에서 탭으로 전환한다.
 *
 * ADMIN      : 부품·재고 목록 · 부품 등록·입고 · 작업별 부품 사용 · 재고 변동 이력 (Figma 24~27) · 부품 요청 관리
 * TECHNICIAN : 부품 사용 · 부품 요청                                           (Figma 36 + 부품 요청)
 *
 * 탭을 숨기는 것은 화면 편의일 뿐이고, 권한은 서버(inventory-service)가 최종 확인한다.
 */
const TABS = {
  ADMIN: [
    { key: "list", label: "부품·재고 목록", element: PartListTab },
    { key: "receive", label: "부품 등록·입고", element: PartReceiveTab },
    { key: "usage", label: "작업별 부품 사용", element: UsageByWorkOrderTab },
    { key: "history", label: "재고 변동 이력", element: StockHistoryTab },
    { key: "requests", label: "부품 요청 관리", element: PartRequestAdminTab },
  ],
  TECHNICIAN: [
    { key: "use", label: "부품 사용", element: PartUseTab },
    { key: "request", label: "부품 요청", element: PartRequestTab },
  ],
};

export default function InventoryPage() {
  const { activeRole, activeOrganizationId, activeOrganizationKind, activeComplexId } = useOrganization();
  // 모든 API 요청에 붙일 조직·단지 (BUILDING 일 때만 단지). 화면을 그리기 전에 정해 둔다
  setInventoryScope({ organizationId: activeOrganizationId, complexId: activeOrganizationKind === "BUILDING" ? activeComplexId : null });
  const tabs = TABS[activeRole] || [];
  const [tab, setTab] = useState(tabs[0]?.key);
  const [prefill, setPrefill] = useState(null);   // 다른 탭으로 넘길 값 (예: 요청할 부품)
  const navigate = (key, payload = null) => { setPrefill(payload); setTab(key); };
  const Current = tabs.find((t) => t.key === tab)?.element;

  return (
    <>
      <PageHeader
        title={activeRole === "TECHNICIAN" ? "부품 사용" : "부품·재고"}
        description={activeRole === "TECHNICIAN"
          ? "배정된 작업에 필요한 부품을 확인하고 사용·요청합니다."
          : "부품 등록부터 입고, 작업별 사용, 재고 변동까지 한 곳에서 관리합니다."}
      />
      <Tabs tabs={tabs} value={tab} onChange={(key) => navigate(key)} />
      {/* 다른 탭에서 바뀐 재고가 보이도록, 탭을 바꿀 때마다 새로 그린다 */}
      {Current && <Current key={tab} onNavigate={navigate} prefill={prefill} />}
    </>
  );
}
