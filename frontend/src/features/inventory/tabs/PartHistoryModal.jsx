import { useState } from "react";
import { Alert, Button, Field, Modal } from "../../../components/ui";
import { inventoryErrorMessage, listMovements, newOperationKey, receiveStock } from "../inventoryApi";
import { Delta, MovementBadge, Pills, StockBadge, Table, formatTime } from "../inventoryUi";
import useLoad from "./useLoad";

/**
 * 부품 하나의 재고 변동 이력 (부품·재고 목록에서 SKU 또는 부품명을 누르면 열림)
 *   이력 조회 : ❌ GET /inventory/movements?partId= 없음 (승인 대기) → 예시 데이터
 *   부품 입고 : ✅ POST /inventory/receipts — 오른쪽 위 "부품 입고" 버튼으로 이 창 안에서 바로 입고
 *
 * onChanged: 입고가 끝나면 부르는 함수 (목록의 수량을 새로 고치기 위해)
 */
export default function PartHistoryModal({ part, onClose, onChanged }) {
  const [type, setType] = useState("ALL");
  const [quantity, setQuantity] = useState(part.quantity);   // 입고 후 바뀐 수량을 창 안에서 바로 보여 주기 위해
  const [receiving, setReceiving] = useState(false);          // 입고 입력칸 열림 여부
  const [notice, setNotice] = useState("");
  const { loading, error, data, reload } = useLoad(() => listMovements({ partId: part.id, type }), [part.id, type]);
  const rows = data || [];
  const totalIn = rows.filter((m) => m.delta > 0).reduce((s, m) => s + m.delta, 0);
  const totalOut = rows.filter((m) => m.delta < 0).reduce((s, m) => s - m.delta, 0);
  const inactive = part.status === "INACTIVE";
  const current = { ...part, quantity };

  function handleReceived(result, qty) {
    setQuantity(result.remainingQuantity);
    setReceiving(false);
    setNotice(`${qty}개를 입고했습니다. 현재 재고 ${result.remainingQuantity}`);
    reload();          // 이 창의 이력 새로 고침
    onChanged?.();     // 뒤의 목록 수량 새로 고침
  }

  const columns = [
    { key: "occurredAt", label: "일시", render: (m) => formatTime(m.occurredAt) },
    { key: "type", label: "구분", render: (m) => <MovementBadge type={m.type} /> },
    { key: "delta", label: "변동", render: (m) => <Delta value={m.delta} /> },
    { key: "remaining", label: "변동 후" },
    { key: "actor", label: "처리자" },
    { key: "note", label: "메모", render: (m) => m.note || "—" },
  ];

  return (
    <Modal open onClose={onClose} title={`${part.sku} · ${part.name}`} description="이 부품의 입고·사용·반납·조정 기록입니다. (⚠️ 이력은 예시 데이터)">
      {/* 요약 + 오른쪽 위 부품 입고 버튼 */}
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap items-center gap-x-5 gap-y-2 text-[13px]">
          <span>현재 <b>{quantity}</b> / 안전 {part.safetyStock} {part.unit || ""}</span>
          <StockBadge part={current} />
          <span className="text-fixops-muted">입고 합계 <b className="text-fixops-primary">+{totalIn}</b> · 사용 합계 <b className="text-fixops-danger">-{totalOut}</b></span>
        </div>
        <Button className="min-h-9 px-4" variant={receiving ? "secondary" : "primary"} disabled={inactive}
          title={inactive ? "비활성 부품에는 입고할 수 없습니다" : undefined}
          onClick={() => { setReceiving((v) => !v); setNotice(""); }}>
          {receiving ? "입고 닫기" : "+ 부품 입고"}
        </Button>
      </div>

      {notice && <div className="mb-3"><Alert tone="success">{notice}</Alert></div>}
      {receiving && <ReceiveForm partId={part.id} onDone={handleReceived} />}

      <div className="mb-3">
        <Pills value={type} onChange={setType}
          options={[{ value: "ALL", label: "전체" }, { value: "RECEIPT", label: "입고" }, { value: "USAGE", label: "사용" }, { value: "STOCK_RETURN", label: "반납" }, { value: "ADJUSTMENT", label: "조정" }]} />
      </div>
      {error && <Alert tone="error">{error}</Alert>}
      <div className="max-h-[360px] overflow-y-auto">
        {loading ? <p className="p-8 text-center text-fixops-muted" role="status">불러오는 중…</p>
          : <Table columns={columns} rows={rows} rowKey={(m) => m.movementId} empty="변동 이력이 없습니다." />}
      </div>
    </Modal>
  );
}

/**
 * 창 안의 입고 입력칸 (POST /inventory/receipts)
 * 멱등 키는 입력칸을 열 때 한 번 만들고, 실패 후 다시 누르면 같은 키로 재시도한다 (두 번 입고 방지).
 */
function ReceiveForm({ partId, onDone }) {
  const [form, setForm] = useState({ quantity: 1, note: "" });
  const [key] = useState(() => newOperationKey("receipt"));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));

  async function submit(e) {
    e.preventDefault();
    setError("");
    const qty = Number(form.quantity);
    if (!Number.isInteger(qty) || qty < 1) return setError("입고 수량은 1 이상이어야 합니다.");
    setSaving(true);
    try {
      const result = await receiveStock({ partId, quantity: qty, note: form.note }, key);
      onDone(result, qty);   // 성공하면 입력칸이 닫히고, 다음에 열 때 새 키가 만들어진다
    } catch (err) {
      setError(inventoryErrorMessage(err));   // 같은 키 유지 → 다시 누르면 재시도
    } finally {
      setSaving(false);
    }
  }

  return (
    <form onSubmit={submit} className="mb-4 grid gap-3 rounded-xl border border-fixops-border bg-fixops-background p-4">
      {error && <Alert tone="error">{error}</Alert>}
      <div className="grid gap-3 sm:grid-cols-[140px_1fr_auto] sm:items-end">
        <Field label="입고 수량" type="number" min="1" value={form.quantity} onChange={set("quantity")} />
        <Field label="메모" placeholder="예: 11월 정기 입고" value={form.note} onChange={set("note")} />
        <Button type="submit" disabled={saving}>{saving ? "입고 중…" : "입고 등록"}</Button>
      </div>
    </form>
  );
}
