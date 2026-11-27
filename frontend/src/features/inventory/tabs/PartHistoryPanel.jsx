import { useState } from "react";
import { Alert, Button, Field } from "../../../components/ui";
import { inventoryErrorMessage, listMovements, newOperationKey, receiveStock } from "../inventoryApi";
import { Delta, MovementBadge, Pills, StockBadge, Table, formatTime } from "../inventoryUi";
import { PartImage } from "../inventoryImages";
import useLoad from "./useLoad";

/**
 * 부품 하나의 재고 변동 이력 (부품·재고 목록에서 SKU 또는 부품명을 누르면 열림)
 *   모달 창이 아니라, 목록 표가 있던 카드 자리를 이 화면으로 바꿔 보여 준다.
 *   오른쪽 위 [← 목록으로] 버튼으로 다시 목록으로 돌아간다.
 *   이력 조회 : ✅ GET /inventory/movements?partId= (D12)
 *   부품 입고 : ✅ POST /inventory/receipts — 오른쪽 위 "부품 입고" 버튼으로 이 창 안에서 바로 입고
 *
 * onBack   : [← 목록으로] 를 누르면 부르는 함수
 * onChanged: 입고가 끝나면 부르는 함수 (목록의 수량을 새로 고치기 위해)
 */
export default function PartHistoryPanel({ part, onBack, onChanged }) {
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
    <div className="p-4">
      {/* 제목 + 오른쪽 위 뒤로가기 */}
      <div className="mb-5 flex flex-wrap items-start justify-between gap-3 border-b border-fixops-border pb-4">
        <div className="flex items-start gap-3">
          <PartImage part={part} />
          <div>
            <h2 className="text-[18px] font-bold">{part.sku} · {part.name}</h2>
            <p className="mt-1 text-[12px] text-fixops-muted">이 부품의 입고·사용·반납·조정 기록입니다.</p>
          </div>
        </div>
        <Button variant="secondary" className="min-h-9 px-4" onClick={onBack}>← 목록으로</Button>
      </div>

      {/* 요약 + 부품 입고 버튼 */}
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
      {loading ? <p className="p-8 text-center text-fixops-muted" role="status">불러오는 중…</p>
        : <Table columns={columns} rows={rows} rowKey={(m) => m.movementId} empty="변동 이력이 없습니다." />}
    </div>
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
