import { useEffect, useRef, useState } from "react";
import { useOrganization } from "./OrganizationProvider";
import { useAuth } from "./AuthProvider";
import { getGeneration } from "../../api/authSession";
import { listComplexMembers, changeComplexAccess } from "./complexApi";
import { explainError, errorCode } from "../../api/errors";
import { Alert, Button, Card, Modal } from "../../components/ui";
import { PageHeader } from "../../components/layout/AppLayout";

const SIZE = 20;
function AccessList({ orgId, complexId }) {
  const { user, clearAuth } = useAuth();
  const [page, setPage] = useState(0);
  const [revision, setRevision] = useState(0);
  const [result, setResult] = useState({ items: [], loading: true, error: "" });
  const [selected, setSelected] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const pending = useRef(false);
  const mounted = useRef(false);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  useEffect(() => {
    const controller = new AbortController();
    setResult({ items: [], loading: true, error: "" });
    listComplexMembers(orgId, complexId, page, SIZE, controller.signal)
      .then(items => { if (!controller.signal.aborted) setResult({ items, loading: false, error: "" }); })
      .catch(e => { if (!controller.signal.aborted) setResult({ items: [], loading: false, error: explainError(e) }); });
    return () => controller.abort();
  }, [orgId, complexId, page, revision]);
  async function confirm() {
    if (!selected || pending.current) return;
    pending.current = true; setBusy(true); setError("");
    const generation = getGeneration();
    try {
      await changeComplexAccess(orgId, complexId, selected.item.member_id, selected.grant);
      if (getGeneration() !== generation) return;
      if (String(selected.item.member_id) === String(user.id)) {
        clearAuth("단지 접근 권한이 변경되었습니다. 다시 로그인해 주세요.");
        return;
      }
      if (!mounted.current) return;
      setSelected(null); setNotice("단지 접근 권한을 변경했습니다."); setRevision(v => v + 1);
    } catch (e) {
      if (mounted.current && getGeneration() === generation) setError(errorCode(e) === "INVALID_STATE_TRANSITION" ? "마지막 단지 관리자의 권한은 회수할 수 없습니다. 최신 권한 상태를 확인해 주세요." : explainError(e));
    } finally { pending.current = false; if (mounted.current) setBusy(false); }
  }
  return <Card className="grid gap-4 p-6">
    <div className="flex items-center justify-between gap-3"><h2 className="font-bold">구성원별 단지 권한</h2><Button variant="secondary" disabled={busy || result.loading} onClick={() => setRevision(v => v + 1)}>새로고침</Button></div>
    <Alert>주민의 세대 접근 권한은 주민 등록 절차에서 관리합니다. 권한 변경 대상 회원은 다시 로그인해야 합니다.</Alert>
    {notice && <Alert tone="success">{notice}</Alert>}
    {result.loading && <p role="status">권한 조회 중…</p>}
    {result.error && <Alert tone="error">{result.error}</Alert>}
    {!result.loading && !result.error && !result.items.length && <p>관리 가능한 구성원이 없습니다.</p>}
    {result.items.map(item => {
      const granted = item.access_status === "ACTIVE" && item.access_scope === item.role;
      return <div key={item.membership_id} className="flex flex-wrap items-center justify-between gap-3 border-t border-fixops-border py-4">
        <div className="min-w-0"><p className="font-semibold">{item.display_name} · {item.role}</p><p className="break-all text-xs text-fixops-muted">{item.email}</p><p className="mt-1 text-sm">{granted ? "접근 허용" : item.access_status === "REVOKED" ? "권한 회수됨" : "접근 권한 없음"}</p></div>
        <Button variant={granted ? "danger" : "secondary"} disabled={busy} onClick={() => { setError(""); setNotice(""); setSelected({ item, grant: !granted }); }}>{granted ? "권한 회수" : "권한 부여"}</Button>
      </div>;
    })}
    <div className="flex items-center justify-between"><Button variant="secondary" disabled={busy || result.loading || page === 0} onClick={() => setPage(p => p - 1)}>이전</Button><span>{page + 1} 페이지</span><Button variant="secondary" disabled={busy || result.loading || result.items.length < SIZE} onClick={() => setPage(p => p + 1)}>다음</Button></div>
    <Modal open={!!selected} title={selected?.grant ? "단지 권한 부여" : "단지 권한 회수"} description={selected?.item.display_name} onClose={() => { if (!pending.current) setSelected(null); }} footer={<><Button variant="secondary" disabled={busy} onClick={() => setSelected(null)}>취소</Button><Button variant={selected?.grant ? "primary" : "danger"} disabled={busy} onClick={confirm}>{busy ? "처리 중…" : "변경 확인"}</Button></>}>
      <p>선택한 단지에 대한 접근 권한을 {selected?.grant ? "부여" : "회수"}합니다.</p>
      {error && <Alert tone="error">{error}</Alert>}
    </Modal>
  </Card>;
}
export default function ComplexAccessPage() {
  const { activeOrganizationId, activeOrganizationKind, activeOrganizationName, activeComplexId, complexes } = useOrganization();
  const complex = complexes.find(c => String(c.id) === String(activeComplexId));
  return <>
    <PageHeader title="단지 접근 권한 관리" description={activeOrganizationName || "업무 조직"} />
    {activeOrganizationKind !== "BUILDING" ? <Alert>공동주택 조직에서만 이용할 수 있습니다.</Alert> : !complex || complex.access_scope !== "ADMIN" ? <Alert>관리 권한이 있는 단지를 선택해 주세요.</Alert> : <>
      <p className="mb-4 font-semibold">{complex.display_name}</p>
      <AccessList key={activeOrganizationId + ":" + activeComplexId} orgId={activeOrganizationId} complexId={activeComplexId} />
    </>}
  </>;
}
