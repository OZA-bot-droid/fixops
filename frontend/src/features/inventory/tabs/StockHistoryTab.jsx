import { useState } from "react";
import { Alert, Card } from "../../../components/ui";
import { listMovements } from "../inventoryApi";
import { Delta, MovementBadge, Pills, Table, formatTime } from "../inventoryUi";
import useLoad from "./useLoad";

/**
 * ADMIN · 재고 변동 이력 (Figma 27) = 원장(stock_movement) 조회
 *   ✅ GET /inventory/movements (D12)
 */

export default function StockHistoryTab() {
  const [type, setType] = useState("ALL");
  const [q, setQ] = useState("");
  const { loading, error, data } = useLoad(() => listMovements({ type, q }), [type, q]);

  const columns = [
    { key: "occurredAt", label: "일시", render: (m) => formatTime(m.occurredAt) },
    { key: "partName", label: "부품", className: "font-semibold" },
    { key: "type", label: "구분", render: (m) => <MovementBadge type={m.type} /> },
    { key: "delta", label: "변동", render: (m) => <Delta value={m.delta} /> },
    { key: "remaining", label: "변동 후 재고" },
    { key: "actor", label: "처리자" },
    { key: "note", label: "메모", render: (m) => m.note || "—" },
  ];

  return (
    <>
      <Card className="mb-5 p-5">
        <div className="flex flex-wrap items-center gap-3">
          <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="⌕ 부품명 검색"
            className="h-10 w-full flex-1 rounded-[9px] sm:w-auto sm:min-w-[260px] bg-fixops-background px-4 text-[13px] outline-none focus:ring-2 focus:ring-blue-100" />
          <Pills value={type} onChange={setType}
            options={[{ value: "ALL", label: "전체" }, { value: "RECEIPT", label: "입고" }, { value: "USAGE", label: "사용" }, { value: "STOCK_RETURN", label: "반납" }, { value: "ADJUSTMENT", label: "조정" }]} />
        </div>
      </Card>
      <Card className="p-3">
        {error && <div className="p-3"><Alert tone="error">{error}</Alert></div>}
        {loading ? <p className="p-8 text-center text-fixops-muted" role="status">불러오는 중…</p>
          : <Table columns={columns} rows={data || []} rowKey={(m) => m.movementId} empty="변동 이력이 없습니다." />}
      </Card>
      <p className="mt-3 text-[12px] text-fixops-muted">원장은 수정·삭제하지 않습니다. 잘못된 기록은 반납·조정 기록을 새로 남겨 바로잡습니다.</p>
    </>
  );
}
