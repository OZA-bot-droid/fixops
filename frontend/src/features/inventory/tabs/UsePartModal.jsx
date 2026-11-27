import { useState } from "react";
import { Alert, Button, Field, Modal } from "../../../components/ui";
import { inventoryErrorMessage, newOperationKey, recordUsage } from "../inventoryApi";

/**
 * 부품 사용 등록 모달 (Figma 36 "부품 사용 등록"). ADMIN·TECHNICIAN 공통.
 * 멱등 키는 모달을 열 때 한 번 만들고, 실패 후 다시 누르면 같은 키로 재시도한다 (중복 차감 방지).
 * 성공하면 다음 사용을 위해 새 키를 만든다.
 */
export default function UsePartModal({ open, onClose, parts, onDone, defaultPartId }) {
  const [form, setForm] = useState({ workOrderId: "", partId: defaultPartId ?? "", quantity: 1 });
  const [key, setKey] = useState(() => newOperationKey("usage"));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));

  async function submit() {
    setError("");
    const body = { workOrderId: Number(form.workOrderId), partId: Number(form.partId), quantity: Number(form.quantity) };
    if (!body.workOrderId || !body.partId || body.quantity < 1) return setError("작업지시 번호, 부품, 수량을 확인해 주세요.");
    setSaving(true);
    try {
      const result = await recordUsage(body, key);
      setKey(newOperationKey("usage"));
      onDone?.(result);
      onClose();
    } catch (e) {
      setError(inventoryErrorMessage(e)); // 같은 key 유지 → 다시 누르면 재시도
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal open={open} onClose={onClose} title="부품 사용 등록"
      description="진행 중(IN_PROGRESS)인 작업에만 사용할 수 있습니다. 사용하면 재고가 바로 차감됩니다."
      footer={<><Button variant="secondary" onClick={onClose}>취소</Button><Button onClick={submit} disabled={saving}>{saving ? "처리 중…" : "사용 등록"}</Button></>}>
      <div className="grid gap-4">
        {error && <Alert tone="error">{error}</Alert>}
        <Field label="작업지시 번호" type="number" min="1" placeholder="예: 1045" value={form.workOrderId} onChange={set("workOrderId")} />
        <Field label="부품" as="select" value={form.partId} onChange={set("partId")}>
          <option value="">부품을 선택하세요</option>
          {parts.map((p) => <option key={p.id} value={p.id} disabled={p.quantity === 0}>{p.sku} · {p.name} (재고 {p.quantity})</option>)}
        </Field>
        <Field label="사용 수량" type="number" min="1" value={form.quantity} onChange={set("quantity")} />
      </div>
    </Modal>
  );
}
