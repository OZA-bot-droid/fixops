import { useRef, useState } from "react";
import { Alert, Button, Field, Modal } from "../../components/ui";
import { allowedTypes, LOCATION_LABELS } from "./locationRules";
import { explainError } from "../../api/errors";

export default function LocationFormModal({ kind, parent, item, onClose, onSave }) {
  const types = allowedTypes(kind, parent);
  const [type, setType] = useState(item?.locationType || types[0] || "");
  const [code, setCode] = useState(item?.code || "");
  const [name, setName] = useState(item?.name || "");
  const [status, setStatus] = useState(item?.status || "ACTIVE");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const pending = useRef(false);
  async function submit(e) {
    e.preventDefault();
    if (pending.current || !code.trim() || !name.trim() || (!item && !types.includes(type))) return;
    pending.current = true; setBusy(true); setError("");
    try {
      await onSave(item ? { code: code.trim(), name: name.trim(), status } :
        { code: code.trim(), name: name.trim(), parentId: parent?.id ?? null, locationType: type });
    } catch (e) { setError(explainError(e)); }
    finally { pending.current = false; setBusy(false); }
  }
  return <Modal open title={item ? "위치 수정" : "위치 등록"} description={parent ? "상위 위치: " + parent.name : item ? item.name : "루트 위치"} onClose={() => { if (!pending.current) onClose(); }}>
    <form className="grid gap-4" onSubmit={submit}>
      <Field label="위치 유형" as="select" value={type} disabled={busy || !!item} onChange={e => setType(e.target.value)} required>
        {(item ? [item.locationType] : types).map(t => <option key={t} value={t}>{LOCATION_LABELS[t]}</option>)}
      </Field>
      <Field label="위치 코드" value={code} maxLength={100} required disabled={busy} onChange={e => setCode(e.target.value)} />
      <Field label="위치명" value={name} maxLength={200} required disabled={busy} onChange={e => setName(e.target.value)} />
      {["BUILDING","UNIT"].includes(type) && <Alert>주민 등록 연동을 위해 동·호수 코드는 입주자 명부의 값과 정확하게 일치해야 합니다. 예: 코드 101, 표시 이름 101동.</Alert>}
      {item && <><Field label="상태" as="select" value={status} disabled={busy} onChange={e => setStatus(e.target.value)}><option value="ACTIVE">활성</option><option value="INACTIVE">비활성</option></Field>
      {(status === "INACTIVE" || code.trim() !== item.code) && <Alert>코드 변경 또는 비활성화는 기존 세대 연결 및 하위 위치 이용에 영향을 줄 수 있습니다. 관련 업무를 확인한 후 저장하세요.</Alert>}</>}
      {error && <Alert tone="error">{error}</Alert>}
      <div className="flex justify-end gap-2"><Button variant="secondary" disabled={busy} onClick={onClose}>취소</Button><Button type="submit" disabled={busy || !code.trim() || !name.trim() || (!item && !types.includes(type))}>{busy ? "저장 중…" : "저장"}</Button></div>
    </form>
  </Modal>;
}
