import { useState } from "react";
import { Alert, Button, Card, Field } from "../../../components/ui";
import { createPartRequest, inventoryErrorMessage, listPartRequests, listParts } from "../inventoryApi";
import { MockNotice, RequestBadge, Table, formatTime } from "../inventoryUi";
import useLoad from "./useLoad";

/**
 * TECHNICIAN · 부품 요청
 *   재고가 없거나 부족한 부품을 관리자에게 요청한다.
 *   - 등록된 부품: 목록에서 고른다 (partId 로 요청)
 *   - 목록에 없는 새 부품: 부품명·SKU·분류·단위·안전 재고를 직접 입력한다 (관리자가 등록할 때 그대로 채워짐)
 *   등록된 부품을 고르면 SKU·분류·단위·안전 재고는 부품 정보로 자동 표시된다 (수정 불가)
 *   "부품 사용" 탭의 [요청] 버튼으로 오면 부품·수량이 미리 채워진다 (prefill).
 *   ❌ API·테이블 설계 전 (새 기능) → 예시 데이터로만 동작. 팀장님과 범위 확정 후 백엔드 추가
 */
const NEW_PART = "__new__";

export default function PartRequestTab({ prefill }) {
  const parts = useLoad((signal) => listParts({ status: "ACTIVE", size: 100 }, signal), []);
  const [form, setForm] = useState({
    partId: prefill?.partId ? String(prefill.partId) : "",
    newPartName: "", sku: "", category: "", unit: "EA", safetyStock: 0,
    quantity: prefill?.quantity ?? 1,
    workOrderId: "",
    reason: "",
  });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const requests = useLoad(() => listPartRequests(), [notice]);
  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));
  const list = parts.data?.content || [];
  const selected = list.find((p) => String(p.id) === form.partId);

  async function submit(e) {
    e.preventDefault();
    setError("");
    const isNew = form.partId === NEW_PART;
    const partName = isNew ? form.newPartName.trim() : selected ? `${selected.sku} · ${selected.name}` : "";
    if (!partName || Number(form.quantity) < 1) return setError("부품과 수량을 확인해 주세요.");
    if (isNew && !form.sku.trim()) return setError("새 부품은 부품 코드(SKU)를 입력해 주세요.");
    if (isNew && list.some((p) => p.sku.toLowerCase() === form.sku.trim().toLowerCase())) return setError("이미 등록된 SKU 입니다. 목록에서 선택해 주세요.");
    setSaving(true);
    try {
      const reason = [form.workOrderId && `WO-${form.workOrderId}`, form.reason].filter(Boolean).join(" · ");
      const info = isNew
        ? { sku: form.sku.trim(), category: form.category.trim() || undefined, unit: form.unit.trim() || undefined, safetyStock: Number(form.safetyStock) || 0 }
        : { sku: selected.sku, category: selected.category, unit: selected.unit, safetyStock: selected.safetyStock };
      await createPartRequest({ partId: isNew ? null : selected.id, partName, ...info, quantity: Number(form.quantity), reason });
      setNotice(`${partName} ${form.quantity}개를 요청했습니다.`);
      setForm({ partId: "", newPartName: "", sku: "", category: "", unit: "EA", safetyStock: 0, quantity: 1, workOrderId: "", reason: "" });
    } catch (err) {
      setError(inventoryErrorMessage(err));
    } finally {
      setSaving(false);
    }
  }

  const columns = [
    { key: "requestedAt", label: "요청 시각", render: (r) => formatTime(r.requestedAt) },
    { key: "partName", label: "부품", className: "font-semibold" },
    { key: "quantity", label: "수량" },
    { key: "reason", label: "사유", render: (r) => r.reason || "—" },
    { key: "status", label: "상태", render: (r) => <div><RequestBadge status={r.status} />{r.rejectReason && <p className="mt-1 text-[11px] text-fixops-muted">{r.rejectReason}</p>}</div> },
  ];

  return (
    <>
      <MockNotice apiMissing />
      {notice && <div className="mb-4"><Alert tone="success">{notice}</Alert></div>}
      <div className="grid gap-5 lg:grid-cols-[400px_1fr]">
        <Card className="h-fit p-6">
          <h2 className="mb-5 text-[18px] font-bold">부품 요청</h2>
          <form onSubmit={submit} className="grid gap-4">
            {error && <Alert tone="error">{error}</Alert>}
            <Field label="부품" as="select" value={form.partId} onChange={set("partId")}>
              <option value="">부품을 선택하세요</option>
              {list.map((p) => <option key={p.id} value={p.id}>{p.sku} · {p.name} (재고 {p.quantity})</option>)}
              <option value={NEW_PART}>+ 목록에 없는 부품 (직접 입력)</option>
            </Field>
            {form.partId === NEW_PART && <Field label="부품명" placeholder="예: 소방 펌프 씰 키트" value={form.newPartName} onChange={set("newPartName")} />}
            {selected && (
              <p className="-mt-2 text-[12px] text-fixops-muted">
                현재 재고 {selected.quantity} / 안전 재고 {selected.safetyStock}
                {selected.quantity < selected.safetyStock && <b className="ml-1 text-fixops-warning">· 부족</b>}
              </p>
            )}
            {/* 부품 정보: 새 부품이면 직접 입력, 등록된 부품이면 자동 표시(수정 불가) */}
            {(selected || form.partId === NEW_PART) && (
              <div className="grid grid-cols-2 gap-3">
                <Field label="부품 코드 (SKU)" placeholder="예: PRT-0010" readOnly={!!selected}
                  value={selected ? selected.sku : form.sku} onChange={set("sku")} className={selected ? "bg-fixops-background" : ""} />
                <Field label="분류" placeholder="예: 펌프" readOnly={!!selected}
                  value={selected ? selected.category || "—" : form.category} onChange={set("category")} className={selected ? "bg-fixops-background" : ""} />
                <Field label="단위" placeholder="EA" readOnly={!!selected}
                  value={selected ? selected.unit || "—" : form.unit} onChange={set("unit")} className={selected ? "bg-fixops-background" : ""} />
                <Field label="안전 재고" type={selected ? "text" : "number"} min="0" readOnly={!!selected}
                  value={selected ? selected.safetyStock : form.safetyStock} onChange={set("safetyStock")} className={selected ? "bg-fixops-background" : ""} />
              </div>
            )}
            {form.partId === NEW_PART && <p className="-mt-2 text-[11px] text-fixops-muted">새 부품 정보는 관리자가 등록할 때 그대로 채워집니다. 관리자가 수정할 수 있습니다.</p>}
            <div className="grid grid-cols-2 gap-3">
              <Field label="수량" type="number" min="1" value={form.quantity} onChange={set("quantity")} />
              <Field label="작업지시 번호 (선택)" type="number" min="1" placeholder="예: 1060" value={form.workOrderId} onChange={set("workOrderId")} />
            </div>
            <Field label="사유" as="textarea" placeholder="예: 교체용, 재고 없음" value={form.reason} onChange={set("reason")} />
            <Button type="submit" disabled={saving}>{saving ? "요청 중…" : "요청 보내기"}</Button>
          </form>
        </Card>
        <Card className="p-6">
          <h2 className="mb-4 text-[16px] font-bold">내 요청 내역</h2>
          {requests.loading ? <p className="p-8 text-center text-fixops-muted" role="status">불러오는 중…</p>
            : <Table columns={columns} rows={requests.data || []} rowKey={(r) => r.requestId} empty="요청 내역이 없습니다." />}
          <p className="mt-3 text-[12px] text-fixops-muted">관리자가 승인하면 입고 후 [부품 사용]에서 사용할 수 있습니다.</p>
        </Card>
      </div>
    </>
  );
}
