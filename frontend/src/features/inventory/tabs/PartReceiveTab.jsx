import { useState } from "react";
import { Alert, Button, Card, Field } from "../../../components/ui";
import { createPart, inventoryErrorMessage, listParts, newOperationKey, receiveStock } from "../inventoryApi";
import { ImagePicker } from "../inventoryImages";
import useLoad from "./useLoad";

/**
 * ADMIN · 부품 등록·입고 (Figma 25)
 *   신규 부품: POST /inventory/parts → (입고 수량이 있으면) POST /inventory/receipts
 *   기존 부품: POST /inventory/receipts
 *   부품 사진: 신규 등록할 때만 1장 (선택). 부품과 같은 요청(multipart)으로 저장한다
 * 멱등 키는 폼마다 하나. 실패 후 다시 저장하면 같은 키로 재시도, 성공하면 새 키.
 */
export default function PartReceiveTab({ prefill }) {
  // 부품 요청 관리에서 [입고 등록]으로 오면: 등록된 부품이면 "기존 부품 입고", 새 부품이면 "신규 부품"으로 미리 채움
  const [mode, setMode] = useState(prefill && !prefill.partId ? "new" : prefill ? "existing" : "new");
  const [notice, setNotice] = useState("");
  return (
    <>
      {notice && <div className="mb-4"><Alert tone="success">{notice}</Alert></div>}
      <div className="grid gap-5 lg:grid-cols-[1fr_340px]">
        <Card className="p-7">
          <div className="mb-6 flex items-center justify-between gap-3">
            <h2 className="text-[18px] font-bold">기본 정보</h2>
            <div className="flex gap-2 text-[12px]">
              {[["new", "신규 부품"], ["existing", "기존 부품 입고"]].map(([k, l]) => (
                <button key={k} type="button" onClick={() => setMode(k)}
                  className={`rounded-full px-4 py-2 font-semibold ${mode === k ? "bg-blue-50 text-fixops-primary" : "bg-fixops-background text-fixops-muted"}`}>{l}</button>
              ))}
            </div>
          </div>
          {mode === "new" ? <NewPartForm onDone={setNotice} prefill={prefill} /> : <ExistingReceiptForm onDone={setNotice} prefill={prefill} />}
        </Card>
        <Card className="h-fit p-6">
          <h2 className="text-[16px] font-bold">입력 가이드</h2>
          <ul className="mt-4 grid gap-2 text-[13px] text-fixops-muted">
            <li>· SKU는 조직 안에서 중복될 수 없습니다.</li>
            <li>· 등록 직후 재고는 0이며, 입고로만 늘어납니다.</li>
            <li>· 안전 재고보다 적으면 목록에 "부족"으로 표시됩니다.</li>
            <li>· 비활성 부품에는 입고할 수 없습니다.</li>
          </ul>
          <span className="mt-5 inline-flex rounded-full bg-emerald-50 px-3 py-1 text-xs font-semibold text-emerald-700">모든 입고는 원장에 기록됩니다</span>
        </Card>
      </div>
    </>
  );
}

function NewPartForm({ onDone, prefill }) {
  const empty = { sku: "", name: "", category: "", unit: "EA", safetyStock: 0, initialQuantity: 0 };
  // 미등록 부품 요청에서 왔으면 기술자가 적은 부품 정보를 채운다 (관리자가 확인·수정 후 저장)
  const [form, setForm] = useState(() => prefill && !prefill.partId
    ? { ...empty, name: prefill.partName, sku: prefill.sku || "", category: prefill.category || "", unit: prefill.unit || "EA", safetyStock: prefill.safetyStock ?? 0, initialQuantity: prefill.quantity }
    : empty);
  const [keys, setKeys] = useState(() => ({ part: newOperationKey("part"), receipt: newOperationKey("receipt") }));
  const [images, setImages] = useState([]);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));

  async function submit(e) {
    e.preventDefault();
    setError("");
    if (!form.sku.trim() || !form.name.trim()) return setError("SKU와 부품명은 필수입니다.");
    setSaving(true);
    try {
      const part = await createPart({ sku: form.sku.trim(), name: form.name.trim(), category: form.category || undefined, unit: form.unit || undefined, safetyStock: Number(form.safetyStock) || 0 }, keys.part, images[0]);
      const qty = Number(form.initialQuantity) || 0;
      if (qty > 0) await receiveStock({ partId: part.id, quantity: qty, note: "최초 입고" }, keys.receipt);
      onDone(`${part.sku} · ${part.name} 을(를) 등록했습니다${qty > 0 ? ` (입고 ${qty})` : ""}.`);
      setForm(empty);
      setImages([]);
      setKeys({ part: newOperationKey("part"), receipt: newOperationKey("receipt") });
    } catch (err) {
      setError(inventoryErrorMessage(err));
    } finally {
      setSaving(false);
    }
  }

  return (
    <form onSubmit={submit} className="grid gap-5">
      {error && <Alert tone="error">{error}</Alert>}
      <div className="grid gap-5 md:grid-cols-2">
        <Field label="부품명" placeholder="예: 급수 펌프 베어링" value={form.name} onChange={set("name")} />
        <Field label="부품 코드 (SKU)" placeholder="예: PRT-0010" value={form.sku} onChange={set("sku")} />
        <Field label="분류" placeholder="예: 펌프" value={form.category} onChange={set("category")} />
        <Field label="단위" placeholder="EA" value={form.unit} onChange={set("unit")} />
        <Field label="안전 재고" type="number" min="0" value={form.safetyStock} onChange={set("safetyStock")} hint="이 수량보다 적으면 부족으로 표시" />
        <Field label="최초 입고 수량" type="number" min="0" value={form.initialQuantity} onChange={set("initialQuantity")} hint="0이면 등록만 합니다" />
      </div>
      <ImagePicker files={images} onChange={setImages} onError={setError} disabled={saving}
        label="부품 사진 (선택, 1장)" hint="부품 모양이나 라벨 사진을 붙이면 기술자가 찾기 쉽습니다." />
      <div className="flex justify-end"><Button type="submit" disabled={saving}>{saving ? "저장 중…" : "저장 및 계속"}</Button></div>
    </form>
  );
}

function ExistingReceiptForm({ onDone, prefill }) {
  const { data } = useLoad((signal) => listParts({ status: "ACTIVE", size: 100 }, signal), []);
  const parts = data?.content || [];
  const [form, setForm] = useState(() => prefill?.partId
    ? { partId: String(prefill.partId), quantity: prefill.quantity, note: `부품 요청 #${prefill.requestId}` }
    : { partId: "", quantity: 1, note: "" });
  const [key, setKey] = useState(() => newOperationKey("receipt"));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));

  async function submit(e) {
    e.preventDefault();
    setError("");
    if (!form.partId || Number(form.quantity) < 1) return setError("부품과 입고 수량을 확인해 주세요.");
    setSaving(true);
    try {
      const r = await receiveStock({ partId: Number(form.partId), quantity: Number(form.quantity), note: form.note }, key);
      onDone(`입고했습니다. 현재 재고 ${r.remainingQuantity}`);
      setForm({ partId: "", quantity: 1, note: "" });
      setKey(newOperationKey("receipt"));
    } catch (err) {
      setError(inventoryErrorMessage(err));
    } finally {
      setSaving(false);
    }
  }

  return (
    <form onSubmit={submit} className="grid gap-5">
      {error && <Alert tone="error">{error}</Alert>}
      <Field label="부품" as="select" value={form.partId} onChange={set("partId")}>
        <option value="">부품을 선택하세요</option>
        {parts.map((p) => <option key={p.id} value={p.id}>{p.sku} · {p.name} (현재 {p.quantity})</option>)}
      </Field>
      <Field label="입고 수량" type="number" min="1" value={form.quantity} onChange={set("quantity")} />
      <Field label="메모" placeholder="예: 11월 정기 입고" value={form.note} onChange={set("note")} />
      <div className="flex justify-end"><Button type="submit" disabled={saving}>{saving ? "저장 중…" : "입고 등록"}</Button></div>
    </form>
  );
}
