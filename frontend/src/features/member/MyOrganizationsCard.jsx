import { useRef, useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "./AuthProvider";
import { useOrganization } from "./OrganizationProvider";
import { leaveOrganization } from "./organizationApi";
import { explainError, errorCode } from "../../api/errors";
import { Alert, Button, Card, Modal } from "../../components/ui";
import { getGeneration } from "../../api/authSession";

export default function MyOrganizationsCard() {
  const { clearAuth } = useAuth();
  const { memberships } = useOrganization();
  const [selected, setSelected] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const pending = useRef(false);
  function close() {
    if (!pending.current) { setSelected(null); setError(""); }
  }
  async function leave() {
    if (!selected || pending.current) return;
    pending.current = true;
    setBusy(true);
    setError("");
    const generation = getGeneration();
    try {
      await leaveOrganization(selected.organizationId);
      if (getGeneration() === generation) {
        clearAuth("조직 탈퇴가 완료되었습니다. 변경된 권한을 적용하려면 다시 로그인해 주세요.");
      }
    } catch (e) {
      if (getGeneration() === generation) {
        setError(errorCode(e) === "INVALID_STATE_TRANSITION"
          ? "탈퇴할 수 없는 상태입니다. 마지막 조직 관리자 또는 단지 관리자 보호 조건을 확인해 주세요."
          : explainError(e));
      }
    } finally { pending.current = false; setBusy(false); }
  }
  return <Card className="grid gap-4 p-7">
    <h2 className="text-lg font-bold">내 소속 조직</h2>
    {!memberships.length && <p className="text-sm text-fixops-muted">소속 조직이 없습니다.</p>}
    {memberships.map(m => <div key={m.membershipId} className="flex flex-wrap items-center justify-between gap-3 border-t border-fixops-border py-3">
      <div className="min-w-0">
        <p className="break-all font-semibold">{m.organizationName || "조직 #" + m.organizationId}</p>
        <p className="text-xs text-fixops-muted">{m.organizationKind === "BUILDING" ? "공동주택" : m.organizationKind === "FACTORY" ? "공장" : "유형 미확인"} · {m.role}</p>
      </div>
      <Button variant="danger" disabled={busy} onClick={() => { setError(""); setSelected(m); }}>조직 탈퇴</Button>
    </div>)}
    <Link to="/organizations/join" className="text-sm font-semibold text-fixops-primary">조직 가입·초대 수락</Link>
    <Modal open={!!selected} title="조직 탈퇴 확인" description={selected?.organizationName || (selected ? "조직 #" + selected.organizationId : "")} onClose={close}
      footer={<><Button variant="secondary" disabled={busy} onClick={close}>취소</Button><Button variant="danger" disabled={busy} onClick={leave}>{busy ? "처리 중…" : "탈퇴 확인"}</Button></>}>
      <Alert>해당 조직의 소속 및 단지·세대 접근 권한이 회수됩니다. 탈퇴 완료 후 다시 로그인해야 합니다. 계정 자체는 삭제되지 않습니다.</Alert>
      {error && <div className="mt-3"><Alert tone="error">{error}</Alert></div>}
    </Modal>
  </Card>;
}
