import { useState } from "react";
import { Alert, Button, Card, Field, Modal } from "../../../components/ui";
import { decidePartRequest, inventoryErrorMessage, listPartRequests } from "../inventoryApi";
import { MockNotice, Pills, RequestBadge, Table, formatTime } from "../inventoryUi";
import useLoad from "./useLoad";

/**
 * ADMIN · 부품 요청 관리
 *   TECHNICIAN 이 보낸 부품 요청을 승인·반려한다.
 *   승인은 "기록만" 한다. 재고는 바뀌지 않고, 실제 입고는 관리자가 따로 등록한다
 *   → 승인된 요청의 [입고 등록] 버튼이 "부품 등록·입고" 탭으로 부품·수량을 채워서 보낸다.
 *   ❌ API·테이블 설계 전 (새 기능) → 예시 데이터
 */
export default function PartRequestAdminTab({ onNavigate }) {
  const [status, setStatus] = useState("REQUESTED");
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");
  const [rejecting, setRejecting] = useState(null);   // 반려 사유를 입력할 요청
  const { loading, data, reload } = useLoad(() => listPartRequests({ status }), [status]);
  const rows = data || [];

  async function decide(req, approve, rejectReason) {
    setError(""); setNotice("");
    try {
      await decidePartRequest(req.requestId, { approve, rejectReason });
      setNotice(approve
        ? `${req.partName} ${req.quantity}개 요청을 승인했습니다. 부품이 들어오면 [입고 등록]으로 입고하세요.`
        : `${req.partName} 요청을 반려했습니다.`);
      setRejecting(null);
      reload();
    } catch (e) {
      setError(inventoryErrorMessage(e));
    }
  }

  const columns = [
    { key: "requestedAt", label: "요청 시각", render: (r) => formatTime(r.requestedAt) },
    { key: "requester", label: "요청자" },
    { key: "partName", label: "부품", render: (r) => (
      <div>
        <p className="font-semibold">{r.partName}{!r.partId && <span className="ml-2 rounded bg-amber-50 px-2 py-0.5 text-[11px] font-normal text-amber-700">미등록 부품</span>}</p>
        {!r.partId && r.sku && <p className="mt-1 text-[11px] text-fixops-muted">{r.sku} · {r.category || "분류 없음"} · {r.unit || "단위 없음"} · 안전 {r.safetyStock ?? 0}</p>}
      </div>
    ) },
    { key: "quantity", label: "수량" },
    { key: "reason", label: "사유", render: (r) => r.reason || "—" },
    { key: "status", label: "상태", render: (r) => <div><RequestBadge status={r.status} />{r.rejectReason && <p className="mt-1 text-[11px] text-fixops-muted">{r.rejectReason}</p>}</div> },
    { key: "actions", label: "", className: "text-right", render: (r) => (
      <div className="flex justify-end gap-2">
        {r.status === "REQUESTED" && <>
          <Button className="min-h-8 px-3 text-[12px]" onClick={() => decide(r, true)}>승인</Button>
          <Button className="min-h-8 px-3 text-[12px]" variant="secondary" onClick={() => setRejecting(r)}>반려</Button>
        </>}
        {r.status === "APPROVED" && (
          <Button className="min-h-8 px-3 text-[12px]" variant="secondary"
            onClick={() => onNavigate("receive", { requestId: r.requestId, partId: r.partId, partName: r.partName, sku: r.sku, category: r.category, unit: r.unit, safetyStock: r.safetyStock, quantity: r.quantity })}>
            입고 등록 →
          </Button>
        )}
      </div>
    ) },
  ];

  return (
    <>
      <MockNotice apiMissing />
      {notice && <div className="mb-4"><Alert tone="success">{notice}</Alert></div>}
      {error && <div className="mb-4"><Alert tone="error">{error}</Alert></div>}
      <Card className="mb-5 flex flex-wrap items-center justify-between gap-3 p-5">
        <Pills value={status} onChange={setStatus}
          options={[{ value: "REQUESTED", label: "대기 중" }, { value: "APPROVED", label: "승인" }, { value: "REJECTED", label: "반려" }, { value: "ALL", label: "전체" }]} />
        <p className="text-[12px] text-fixops-muted">승인은 기록만 합니다. 재고는 입고를 등록해야 늘어납니다.</p>
      </Card>
      <Card className="p-3">
        {loading ? <p className="p-8 text-center text-fixops-muted" role="status">불러오는 중…</p>
          : <Table columns={columns} rows={rows} rowKey={(r) => r.requestId} empty="해당하는 요청이 없습니다." />}
      </Card>
      {rejecting && <RejectModal request={rejecting} onClose={() => setRejecting(null)} onSubmit={(reason) => decide(rejecting, false, reason)} />}
    </>
  );
}

function RejectModal({ request, onClose, onSubmit }) {
  const [reason, setReason] = useState("");
  return (
    <Modal open onClose={onClose} title="부품 요청 반려" description={`${request.requester} · ${request.partName} ${request.quantity}개`}
      footer={<><Button variant="secondary" onClick={onClose}>취소</Button><Button variant="danger" disabled={!reason.trim()} onClick={() => onSubmit(reason.trim())}>반려</Button></>}>
      <Field label="반려 사유" as="textarea" placeholder="예: 기존 PRT-0001로 대체 가능" value={reason} onChange={(e) => setReason(e.target.value)} hint="요청한 기술자에게 그대로 보입니다." />
    </Modal>
  );
}
