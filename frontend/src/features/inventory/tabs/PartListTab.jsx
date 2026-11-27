import { useRef, useState } from "react";
import { Alert, Button, Card } from "../../../components/ui";
import { listParts } from "../inventoryApi";
import { Pagination, Pills, StockBadge, Table } from "../inventoryUi";
import PartHistoryPanel from "./PartHistoryPanel";
import { PartCover, PartImage } from "../inventoryImages";
import useLoad from "./useLoad";

/**
 * ADMIN · 부품·재고 목록 (Figma 24) — GET /inventory/parts
 *   보기 방식 2가지 (오른쪽 위 [카드 | 표])
 *     카드: 쇼핑몰 검색 결과처럼 사진이 위, 부품 정보가 아래인 카드를 격자로 나열 (기본)
 *     표  : 한 줄에 한 부품
 *   한 쪽에 20개 (PC 에서 5칸 × 4줄), 그 뒤는 아래 쪽 번호로 넘겨 본다 (서버 page·size 사용)
 *   카드를 누르거나 SKU·부품명을 누르면 목록 자리가 그 부품의 재고 변동 이력으로 바뀐다
 *   (PartHistoryPanel, [← 목록으로]로 복귀)
 */
const PAGE_SIZE = 20;   // 5칸 × 4줄

export default function PartListTab({ onNavigate }) {
  const [q, setQ] = useState("");
  const [status, setStatus] = useState("ACTIVE");
  const [lowStockOnly, setLowStockOnly] = useState(false);
  const [page, setPage] = useState(0);              // 0부터 (화면에는 1부터)
  const top = useRef(null);
  // 검색어·필터가 바뀌면 1쪽부터 다시
  const filter = (setter) => (v) => { setter(v); setPage(0); };
  const goPage = (n) => { setPage(n); top.current?.scrollIntoView({ behavior: "smooth", block: "start" }); };
  const [selected, setSelected] = useState(null);   // 이력을 볼 부품
  const [view, setView] = useState(() => readView()); // "card" | "table" (마지막으로 고른 방식을 기억)
  const changeView = (v) => { setView(v); saveView(v); };
  const { loading, error, data, reload } = useLoad((signal) => listParts({ q, status, lowStockOnly, page, size: PAGE_SIZE }, signal), [q, status, lowStockOnly, page]);
  const parts = data?.content || [];

  const columns = [
    { key: "sku", label: "SKU", render: (p) => <HistoryLink part={p} onOpen={setSelected} bold>{p.sku}</HistoryLink> },
    { key: "name", label: "부품명", render: (p) => (
      <div className="flex items-center gap-2">
        <PartImage part={p} className="h-9 w-9" />
        <HistoryLink part={p} onOpen={setSelected}>{p.name}</HistoryLink>
      </div>
    ) },
    { key: "category", label: "분류", render: (p) => p.category || "—" },
    { key: "quantity", label: "현재 / 안전 재고", render: (p) => <span><b>{p.quantity}</b> <span className="text-fixops-muted">/ {p.safetyStock} {p.unit || ""}</span></span> },
    { key: "status", label: "상태", render: (p) => <StockBadge part={p} /> },
  ];

  return (
    <>
      <Card className="mb-5 scroll-mt-4 p-5" ref={top}>
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div className="grid flex-1 gap-3">
            <div className="flex items-center justify-between gap-3">
              <p className="text-[14px] font-semibold">전체 항목 <span className="text-fixops-muted">{data ? data.totalElements : ""}</span></p>
              <Pills value={view} onChange={changeView} options={[{ value: "card", label: "카드" }, { value: "table", label: "표" }]} />
            </div>
            <div className="flex flex-wrap items-center gap-3">
              <input value={q} onChange={(e) => filter(setQ)(e.target.value)} placeholder="⌕ SKU, 부품명 검색"
                className="h-10 w-full flex-1 rounded-[9px] sm:w-auto sm:min-w-[260px] bg-fixops-background px-4 text-[13px] outline-none focus:ring-2 focus:ring-blue-100" />
              <Pills value={status} onChange={filter(setStatus)}
                options={[{ value: "ACTIVE", label: "사용 중" }, { value: "INACTIVE", label: "비활성" }, { value: "ALL", label: "전체" }]} />
              <label className="flex items-center gap-2 text-[12px] text-fixops-muted">
                <input type="checkbox" checked={lowStockOnly} onChange={(e) => filter(setLowStockOnly)(e.target.checked)} /> 안전 재고 미만만
              </label>
            </div>
          </div>
          <Button onClick={() => onNavigate("receive")}>+ 신규 등록</Button>
        </div>
      </Card>
      {/* 같은 카드 자리에서 "목록 표" ↔ "선택한 부품의 변동 이력"을 바꿔 보여 준다 */}
      <Card className="p-3">
        {selected ? (
          <PartHistoryPanel part={selected} onBack={() => setSelected(null)} onChanged={reload} />
        ) : (
          <>
            {error && <div className="p-3"><Alert tone="error">{error}</Alert></div>}
            {loading ? <p className="p-8 text-center text-fixops-muted" role="status">불러오는 중…</p>
              : view === "card" ? <PartGrid parts={parts} onOpen={setSelected} />
              : <Table columns={columns} rows={parts} rowKey={(p) => p.id} empty="조건에 맞는 부품이 없습니다." />}
            {!loading && data && <Pagination page={page} size={PAGE_SIZE} total={data.totalElements} onChange={goPage} />}
          </>
        )}
      </Card>
      {!selected && <p className="mt-3 text-[12px] text-fixops-muted">{view === "card" ? "카드를" : "SKU 또는 부품명을"} 누르면 그 부품의 재고 변동 이력을 볼 수 있습니다.</p>}
    </>
  );
}

/**
 * 카드 격자 (쇼핑몰 검색 결과 형식)
 *   휴대폰 2칸 → 작은 태블릿 3칸 → 태블릿 4칸 → PC 5칸 (한 쪽 20개 = PC 에서 5 × 4)
 *   카드: [사진] → SKU → 부품명(2줄까지) → 현재 재고(크게) → 안전 재고 → 상태·분류 배지
 */
function PartGrid({ parts, onOpen }) {
  if (!parts.length) return <p className="p-8 text-center text-fixops-muted">조건에 맞는 부품이 없습니다.</p>;
  return (
    <ul className="grid grid-cols-2 gap-3 p-1 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5">
      {parts.map((p) => {
        const low = p.status === "ACTIVE" && p.quantity < p.safetyStock;
        return (
          <li key={p.id}>
            <button type="button" onClick={() => onOpen(p)} title="재고 변동 이력 보기"
              className={`group flex h-full w-full flex-col overflow-hidden rounded-xl border bg-white text-left transition hover:-translate-y-0.5 hover:shadow-md ${p.status === "INACTIVE" ? "opacity-60" : ""} ${low ? "border-amber-200" : "border-fixops-border"}`}>
              <PartCover part={p} />
              <div className="flex flex-1 flex-col gap-1 p-3">
                <p className="text-[11px] font-semibold text-fixops-muted">{p.sku}</p>
                <p className="line-clamp-2 min-h-[2.5em] text-[13px] font-medium leading-[1.25] group-hover:text-fixops-primary">{p.name}</p>
                <p className="mt-1 text-[18px] font-bold leading-none">
                  {p.quantity.toLocaleString()}<span className="ml-1 text-[12px] font-semibold text-fixops-muted">{p.unit || "EA"}</span>
                </p>
                <p className={`text-[11px] ${low ? "font-semibold text-amber-700" : "text-fixops-muted"}`}>
                  안전 재고 {p.safetyStock}{low && ` · ${p.safetyStock - p.quantity}개 부족`}
                </p>
                <div className="mt-auto flex flex-wrap items-center gap-1.5 pt-2">
                  <StockBadge part={p} />
                  {p.category && <span className="rounded-full bg-fixops-background px-2.5 py-1 text-[11px] text-fixops-muted">{p.category}</span>}
                </div>
              </div>
            </button>
          </li>
        );
      })}
    </ul>
  );
}

// 보기 방식 기억 (브라우저 저장소. 못 쓰는 환경이면 기본값 "카드")
function readView() {
  try { return localStorage.getItem("fixops.inventory.partView") === "table" ? "table" : "card"; } catch { return "card"; }
}
function saveView(v) {
  try { localStorage.setItem("fixops.inventory.partView", v); } catch { /* 저장 못 해도 화면은 동작 */ }
}

/** 표 안의 클릭 가능한 글자 (밑줄 → 이력 열기) */
function HistoryLink({ part, onOpen, bold = false, children }) {
  return (
    <button type="button" onClick={() => onOpen(part)} title="재고 변동 이력 보기"
      className={`text-left underline-offset-4 hover:text-fixops-primary hover:underline ${bold ? "font-semibold" : ""}`}>
      {children}
    </button>
  );
}
