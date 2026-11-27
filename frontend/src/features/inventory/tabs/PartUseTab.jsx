import { useRef, useState } from "react";
import { Alert, Button, Card } from "../../../components/ui";
import { listParts, listUsages } from "../inventoryApi";
import { MockNotice, Pills, StatCard, StockBadge, Table, formatTime } from "../inventoryUi";
import UsePartModal from "./UsePartModal";
import useLoad from "./useLoad";

/**
 * TECHNICIAN · 부품 사용 (Figma 36)
 *   부품 재고 조회 ✅ GET /inventory/parts (활성 부품만, 조회 전용)
 *   사용 등록     ✅ POST /inventory/usages  — 줄마다 [사용] 버튼, 또는 아래 "부품 사용 등록"
 *   부품 요청     → 재고가 없거나 부족하면 줄의 [요청] 버튼으로 "부품 요청" 탭에 부품을 채워서 이동
 *   내 사용 내역  ❌ 조회 API 없음 → 예시 데이터
 *   요약 카드 4개를 누르면 그 값에 해당하는 목록으로 조회된다
 *     사용 가능 부품 → 부품 표 "사용 가능" / 부족 부품 → 부품 표 "부족"
 *     오늘 사용 → 사용 내역 "오늘" / 최근 사용 → 사용 내역 "전체"
 *
 * TECHNICIAN 은 입고·등록·수정을 할 수 없다 (버튼 자체가 없음, 서버도 403).
 */
export default function PartUseTab({ onNavigate }) {
  const [q, setQ] = useState("");
  const [filter, setFilter] = useState("ALL");
  const [usageFilter, setUsageFilter] = useState("RECENT");   // RECENT: 최근 5건 / TODAY: 오늘 전부 / ALL: 전부
  const partsRef = useRef(null);
  const usagesRef = useRef(null);
  const [usePart, setUsePart] = useState(undefined);   // undefined: 닫힘, null: 부품 미선택으로 열림, 객체: 그 부품으로 열림
  const [notice, setNotice] = useState("");
  const parts = useLoad((signal) => listParts({ q, status: "ACTIVE", size: 100 }, signal), [q, notice]);
  const usages = useLoad(() => listUsages(), [notice]);
  const all = parts.data?.content || [];
  const isShort = (p) => p.quantity < p.safetyStock;
  const list = all.filter((p) => filter === "ALL" || (filter === "SHORT" ? isShort(p) : p.quantity > 0));
  const today = new Date().toDateString();
  const isToday = (u) => new Date(u.occurredAt).toDateString() === today;
  const allUsages = usages.data || [];
  const todayCount = allUsages.filter(isToday).length;
  const shownUsages = usageFilter === "TODAY" ? allUsages.filter(isToday) : usageFilter === "ALL" ? allUsages : allUsages.slice(0, 5);

  // 카드를 누르면: 필터를 바꾸고 해당 표로 스크롤. 이미 선택된 카드를 다시 누르면 해제
  const scrollTo = (ref) => setTimeout(() => ref.current?.scrollIntoView({ behavior: "smooth", block: "start" }), 0);
  const pickParts = (value) => { setFilter((f) => (f === value ? "ALL" : value)); scrollTo(partsRef); };
  const pickUsages = (value) => { setUsageFilter((f) => (f === value ? "RECENT" : value)); scrollTo(usagesRef); };

  const columns = [
    { key: "sku", label: "SKU", className: "font-semibold" },
    { key: "name", label: "부품명" },
    { key: "quantity", label: "재고", render: (p) => <span><b>{p.quantity}</b> <span className="text-fixops-muted">{p.unit || ""}</span></span> },
    { key: "status", label: "상태", render: (p) => <StockBadge part={p} /> },
    { key: "actions", label: "", className: "text-right", render: (p) => (
      <div className="flex justify-end gap-2">
        <Button className="min-h-8 px-3 text-[12px]" disabled={p.quantity === 0} onClick={() => setUsePart(p)}>사용</Button>
        <Button className="min-h-8 px-3 text-[12px]" variant={isShort(p) ? "primary" : "secondary"}
          onClick={() => onNavigate("request", { partId: p.id, partName: `${p.sku} · ${p.name}`, quantity: Math.max(p.safetyStock - p.quantity, 1) })}>
          요청
        </Button>
      </div>
    ) },
  ];
  const recent = [
    { key: "workOrderId", label: "작업지시", className: "font-semibold", render: (u) => `WO-${u.workOrderId}` },
    { key: "partName", label: "부품" },
    { key: "quantity", label: "수량" },
    { key: "occurredAt", label: "시각", render: (u) => formatTime(u.occurredAt) },
  ];

  return (
    <>
      <MockNotice />
      {notice && <div className="mb-4"><Alert tone="success">{notice}</Alert></div>}
      <div className="mb-5 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard label="사용 가능 부품" value={`${all.filter((p) => p.quantity > 0).length}종`}
          onClick={() => pickParts("AVAILABLE")} active={filter === "AVAILABLE"} />
        <StatCard label="부족 부품" value={`${all.filter(isShort).length}종`} tone="text-fixops-warning"
          onClick={() => pickParts("SHORT")} active={filter === "SHORT"} />
        <StatCard label="오늘 사용" value={`${todayCount}건`}
          onClick={() => pickUsages("TODAY")} active={usageFilter === "TODAY"} />
        <StatCard label="최근 사용" value={`${allUsages.length}건`}
          onClick={() => pickUsages("ALL")} active={usageFilter === "ALL"} />
      </div>

      <Card className="mb-5 scroll-mt-6 p-6" ref={partsRef}>
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <h2 className="text-[18px] font-bold">작업별 부품 및 재고</h2>
          <div className="flex flex-wrap items-center gap-3">
            <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="⌕ SKU, 부품명 검색"
              className="h-10 w-[220px] rounded-[9px] bg-fixops-background px-4 text-[13px] outline-none focus:ring-2 focus:ring-blue-100" />
            <Pills value={filter} onChange={setFilter}
              options={[{ value: "ALL", label: "전체" }, { value: "AVAILABLE", label: "사용 가능" }, { value: "SHORT", label: "부족" }]} />
          </div>
        </div>
        {parts.error && <Alert tone="error">{parts.error}</Alert>}
        {parts.loading ? <p className="p-8 text-center text-fixops-muted" role="status">불러오는 중…</p>
          : <Table columns={columns} rows={list} rowKey={(p) => p.id} empty="조건에 맞는 부품이 없습니다." />}
        <p className="mt-3 text-[12px] text-fixops-muted">재고가 없거나 부족하면 [요청]으로 관리자에게 부품을 요청하세요.</p>
      </Card>

      <Card className="mb-5 scroll-mt-6 p-6" ref={usagesRef}>
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <h2 className="text-[16px] font-bold">사용 내역 <span className="text-[12px] font-normal text-fixops-muted">(예시 데이터)</span></h2>
          <Pills value={usageFilter} onChange={setUsageFilter}
            options={[{ value: "RECENT", label: "최근 5건" }, { value: "TODAY", label: "오늘" }, { value: "ALL", label: "전체" }]} />
        </div>
        <Table columns={recent} rows={shownUsages} rowKey={(u) => u.usageId}
          empty={usageFilter === "TODAY" ? "오늘 사용한 부품이 없습니다." : "아직 사용 내역이 없습니다."} />
      </Card>

      <Card className="flex flex-wrap items-center justify-between gap-3 p-5">
        <p className="text-[13px] text-fixops-muted">배정된 작업이 진행 중(IN_PROGRESS)일 때만 부품을 사용할 수 있습니다.</p>
        <Button onClick={() => setUsePart(null)}>부품 사용 등록</Button>
      </Card>

      {usePart !== undefined && (
        <UsePartModal open onClose={() => setUsePart(undefined)} parts={all} defaultPartId={usePart?.id}
          onDone={(r) => setNotice(`부품을 사용했습니다. 남은 재고 ${r.remainingQuantity}`)} />
      )}
    </>
  );
}
