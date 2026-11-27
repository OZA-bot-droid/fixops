import { useState } from "react";
import { Alert, Button, Card } from "../../../components/ui";
import { listParts, listUsages } from "../inventoryApi";
import { Table, formatTime } from "../inventoryUi";
import UsePartModal from "./UsePartModal";
import useLoad from "./useLoad";

/**
 * ADMIN · 작업별 부품 사용 (Figma 26)
 *   사용 내역 조회: ✅ GET /inventory/usages?workOrderId= (D12)
 *   사용 등록    : ✅ POST /inventory/usages
 */
export default function UsageByWorkOrderTab() {
  const [workOrderId, setWorkOrderId] = useState("");
  const [open, setOpen] = useState(false);
  const usages = useLoad(() => listUsages({ workOrderId }), [workOrderId]);
  const parts = useLoad((signal) => listParts({ status: "ACTIVE", size: 100 }, signal), [open]);

  const columns = [
    { key: "workOrder", label: "작업지시", className: "font-semibold", render: (u) => <>WO-{u.workOrderId} <span className="font-normal text-fixops-muted">{u.workOrderTitle}</span></> },
    { key: "partName", label: "부품" },
    { key: "quantity", label: "수량", render: (u) => `${u.quantity}` },
    { key: "memberName", label: "사용자" },
    { key: "occurredAt", label: "사용 시각", render: (u) => formatTime(u.occurredAt) },
  ];

  return (
    <>
      <Card className="mb-5 p-5">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <input value={workOrderId} onChange={(e) => setWorkOrderId(e.target.value.replace(/\D/g, ""))} placeholder="⌕ 작업지시 번호로 찾기 (예: 1045)"
            className="h-10 w-full flex-1 rounded-[9px] sm:w-auto sm:min-w-[260px] bg-fixops-background px-4 text-[13px] outline-none focus:ring-2 focus:ring-blue-100" />
          <Button onClick={() => setOpen(true)}>+ 부품 사용 등록</Button>
        </div>
      </Card>
      <Card className="p-3">
        {usages.error && <div className="p-3"><Alert tone="error">{usages.error}</Alert></div>}
        {usages.loading ? <p className="p-8 text-center text-fixops-muted" role="status">불러오는 중…</p>
          : <Table columns={columns} rows={usages.data || []} rowKey={(u) => u.usageId} empty="사용 내역이 없습니다." />}
      </Card>
      {open && <UsePartModal open onClose={() => setOpen(false)} parts={parts.data?.content || []} onDone={usages.reload} />}
    </>
  );
}
