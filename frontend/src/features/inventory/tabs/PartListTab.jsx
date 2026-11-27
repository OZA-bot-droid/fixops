import { useState } from "react";
import { Alert, Button, Card } from "../../../components/ui";
import { listParts } from "../inventoryApi";
import { MockNotice, Pills, StockBadge, Table } from "../inventoryUi";
import PartHistoryModal from "./PartHistoryModal";
import useLoad from "./useLoad";

/**
 * ADMIN · 부품·재고 목록 (Figma 24) — GET /inventory/parts
 * SKU 또는 부품명을 누르면 그 부품의 재고 변동 이력이 열린다 (PartHistoryModal)
 */
export default function PartListTab({ onNavigate }) {
  const [q, setQ] = useState("");
  const [status, setStatus] = useState("ACTIVE");
  const [lowStockOnly, setLowStockOnly] = useState(false);
  const [selected, setSelected] = useState(null);   // 이력을 볼 부품
  const { loading, error, data, reload } = useLoad((signal) => listParts({ q, status, lowStockOnly }, signal), [q, status, lowStockOnly]);
  const parts = data?.content || [];

  const columns = [
    { key: "sku", label: "SKU", render: (p) => <HistoryLink part={p} onOpen={setSelected} bold>{p.sku}</HistoryLink> },
    { key: "name", label: "부품명", render: (p) => <HistoryLink part={p} onOpen={setSelected}>{p.name}</HistoryLink> },
    { key: "category", label: "분류", render: (p) => p.category || "—" },
    { key: "quantity", label: "현재 / 안전 재고", render: (p) => <span><b>{p.quantity}</b> <span className="text-fixops-muted">/ {p.safetyStock} {p.unit || ""}</span></span> },
    { key: "status", label: "상태", render: (p) => <StockBadge part={p} /> },
  ];

  return (
    <>
      <MockNotice />
      <Card className="mb-5 p-5">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div className="grid flex-1 gap-3">
            <p className="text-[14px] font-semibold">전체 항목 <span className="text-fixops-muted">{data ? data.totalElements : ""}</span></p>
            <div className="flex flex-wrap items-center gap-3">
              <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="⌕ SKU, 부품명 검색"
                className="h-10 min-w-[260px] flex-1 rounded-[9px] bg-fixops-background px-4 text-[13px] outline-none focus:ring-2 focus:ring-blue-100" />
              <Pills value={status} onChange={setStatus}
                options={[{ value: "ACTIVE", label: "사용 중" }, { value: "INACTIVE", label: "비활성" }, { value: "ALL", label: "전체" }]} />
              <label className="flex items-center gap-2 text-[12px] text-fixops-muted">
                <input type="checkbox" checked={lowStockOnly} onChange={(e) => setLowStockOnly(e.target.checked)} /> 안전 재고 미만만
              </label>
            </div>
          </div>
          <Button onClick={() => onNavigate("receive")}>+ 신규 등록</Button>
        </div>
      </Card>
      <Card className="p-3">
        {error && <div className="p-3"><Alert tone="error">{error}</Alert></div>}
        {loading ? <p className="p-8 text-center text-fixops-muted" role="status">불러오는 중…</p>
          : <Table columns={columns} rows={parts} rowKey={(p) => p.id} empty="조건에 맞는 부품이 없습니다." />}
      </Card>
      <p className="mt-3 text-[12px] text-fixops-muted">SKU 또는 부품명을 누르면 그 부품의 재고 변동 이력을 볼 수 있습니다.</p>
      {selected && <PartHistoryModal part={selected} onClose={() => setSelected(null)} onChanged={reload} />}
    </>
  );
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
