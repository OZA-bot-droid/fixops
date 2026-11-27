import { useEffect, useRef, useState } from "react";
import { useAuth } from "./AuthProvider";
import { useOrganization } from "./OrganizationProvider";
import * as api from "./organizationApi";
import { explainError, errorCode } from "../../api/errors";
import { Alert, Button, Card, Field, Modal } from "../../components/ui";
import { PageHeader } from "../../components/layout/AppLayout";

const SIZE = 20;
function usePage(load, orgId, revision) {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState({ items: [], loading: true, error: "" });
  useEffect(() => {
    const controller = new AbortController();
    setResult({ items: [], loading: true, error: "" });
    load(orgId, page, SIZE, controller.signal).then(items => {
      if (!controller.signal.aborted) setResult({ items, loading: false, error: "" });
    }).catch(e => {
      if (!controller.signal.aborted) setResult({ items: [], loading: false, error: explainError(e) });
    });
    return () => controller.abort();
  }, [load, orgId, page, revision]);
  return { ...result, page, setPage };
}
function Pager({ data, busy }) {
  return <div className="flex items-center justify-between gap-2">
    <Button variant="secondary" disabled={busy || data.loading || data.page === 0} onClick={() => data.setPage(p => p - 1)}>이전</Button>
    <span className="text-sm">{data.page + 1} 페이지</span>
    <Button variant="secondary" disabled={busy || data.loading || data.items.length < SIZE} onClick={() => data.setPage(p => p + 1)}>다음</Button>
  </div>;
}
export default function MemberManagementPage() {
  const { user, clearAuth } = useAuth();
  const { activeOrganizationId: orgId, activeOrganizationName } = useOrganization();
  const [revision, setRevision] = useState(0);
  const members = usePage(api.listMembers, orgId, revision);
  const requests = usePage(api.pendingJoinRequests, orgId, revision);
  const [action, setAction] = useState(null);
  const [role, setRole] = useState("USER");
  const [email, setEmail] = useState("");
  const [invitation, setInvitation] = useState(null);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");
  const pending = useRef(false);
  const mounted = useRef(false);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  function open(next, initialRole = "USER") {
    setError(""); setNotice(""); setInvitation(null); setEmail(""); setRole(initialRole); setAction(next);
  }
  function close() { if (!pending.current) { setAction(null); setInvitation(null); setError(""); } }
  async function confirm(e) {
    e?.preventDefault();
    if (pending.current || !action) return;
    pending.current = true; setBusy(true); setError(""); setNotice("");
    const current = action;
    try {
      if (current.type === "invite") {
        const data = await api.inviteMember(orgId, email.trim(), role);
        if (mounted.current) setInvitation(data);
        return;
      }
      if (current.type === "decision") await api.decideJoinRequest(orgId, current.item.id, current.approve);
      if (current.type === "role") await api.changeMemberRole(orgId, current.item.member_id, role);
      if (current.type === "remove") await api.deactivateMember(orgId, current.item.member_id);
      if (!mounted.current) return;
      if (current.type === "role" && String(current.item.member_id) === String(user.id) && current.item.role !== role) {
        clearAuth("권한이 변경되었습니다. 다시 로그인해 주세요."); return;
      }
      setAction(null); setNotice("변경사항을 반영했습니다."); setRevision(v => v + 1);
    } catch (e) {
      if (mounted.current) setError(errorCode(e) === "INVALID_STATE_TRANSITION"
        ? "현재 상태에서는 변경할 수 없습니다. 마지막 조직 관리자 또는 단지 관리자 보호 조건과 최신 상태를 확인해 주세요."
        : explainError(e));
    } finally { pending.current = false; if (mounted.current) setBusy(false); }
  }
  const title = action?.type === "invite" ? "구성원 초대" : action?.type === "role" ? "역할 변경" : action?.type === "remove" ? "구성원 비활성화" : action?.approve ? "가입 승인" : "가입 거절";
  return <>
    <PageHeader title="회원·역할 관리" description={activeOrganizationName || "조직 #" + orgId}
      action={<Button disabled={busy} onClick={() => open({ type: "invite" })}>구성원 초대</Button>} />
    {notice && <Alert tone="success">{notice}</Alert>}
    <div className="grid gap-6">
      <Card className="grid gap-4 p-6">
        <div className="flex items-center justify-between"><h2 className="font-bold">가입 승인 대기</h2><Button variant="secondary" disabled={busy} onClick={() => setRevision(v => v + 1)}>새로고침</Button></div>
        {requests.loading && <p role="status">조회 중…</p>}
        {requests.error && <Alert tone="error">{requests.error}</Alert>}
        {!requests.loading && !requests.error && !requests.items.length && <p>승인 대기 신청이 없습니다.</p>}
        {requests.items.map(item => <div key={item.id} className="flex flex-wrap items-center justify-between gap-3 border-t border-fixops-border py-3">
          <span>회원 #{item.member_id} · 신청 #{item.id}</span>
          <div className="flex gap-2"><Button disabled={busy} onClick={() => open({ type: "decision", item, approve: true })}>승인</Button><Button variant="secondary" disabled={busy} onClick={() => open({ type: "decision", item, approve: false })}>거절</Button></div>
        </div>)}
        <Pager data={requests} busy={busy} />
      </Card>
      <Card className="grid gap-4 p-6">
        <h2 className="font-bold">활성 구성원</h2>
        {members.loading && <p role="status">조회 중…</p>}
        {members.error && <Alert tone="error">{members.error}</Alert>}
        {!members.loading && !members.error && !members.items.length && <p>구성원이 없습니다.</p>}
        {members.items.map(item => <div key={item.membership_id} className="flex flex-wrap items-center justify-between gap-3 border-t border-fixops-border py-3">
          <div className="min-w-0"><p className="font-semibold">{item.display_name} · {item.role}</p><p className="break-all text-xs text-fixops-muted">{item.email}</p></div>
          <div className="flex gap-2"><Button variant="secondary" disabled={busy} onClick={() => open({ type: "role", item }, item.role)}>역할 변경</Button><Button variant="danger" disabled={busy || String(item.member_id) === String(user.id)} onClick={() => open({ type: "remove", item })}>비활성화</Button></div>
        </div>)}
        <Pager data={members} busy={busy} />
      </Card>
    </div>
    <Modal open={!!action} title={title} onClose={close}>
      {action && <form className="grid gap-4" onSubmit={confirm}>
        {action.type === "invite" && (invitation ? <>
          <Alert tone="success">초대 코드가 생성되었습니다. 지정된 이메일의 회원에게 안전하게 전달하세요. 유효기간 24시간이며 자동 이메일은 발송되지 않습니다.</Alert>
          <Field label="초대 코드" value={invitation.token || ""} readOnly />
          <Button variant="secondary" onClick={() => { navigator.clipboard?.writeText(invitation.token).then(() => setNotice("초대 코드를 복사했습니다.")).catch(() => setError("직접 선택하여 복사해 주세요.")); }}>복사</Button>
          {notice && <Alert tone="success">{notice}</Alert>}
        </> : <><Field label="초대 이메일" type="email" maxLength={255} required value={email} onChange={e => setEmail(e.target.value)} /><Field label="역할" as="select" value={role} onChange={e => setRole(e.target.value)}><option value="USER">USER</option><option value="TECHNICIAN">TECHNICIAN</option></Field></>)}
        {action.type === "role" && <><p>{action.item.display_name}</p><Field label="새 역할" as="select" value={role} onChange={e => setRole(e.target.value)}>{["ADMIN","TECHNICIAN","USER"].map(r => <option key={r}>{r}</option>)}</Field><Alert>역할 변경 시 기존 단지 및 세대 권한이 회수되며 재로그인이 필요합니다.</Alert></>}
        {action.type === "remove" && <Alert>해당 구성원의 조직 소속 및 관련 접근 권한을 회수합니다.</Alert>}
        {action.type === "decision" && <Alert>{action.approve ? "승인 시 USER 역할을 부여합니다. 단지 권한은 별도 절차로 설정합니다." : "가입 신청을 거절합니다."}</Alert>}
        {error && <Alert tone="error">{error}</Alert>}
        <div className="flex justify-end gap-2"><Button variant="secondary" disabled={busy} onClick={close}>{invitation ? "닫기" : "취소"}</Button>{!invitation && <Button type="submit" disabled={busy || (action.type === "role" && role === action.item.role)}>{busy ? "처리 중…" : "확인"}</Button>}</div>
      </form>}
    </Modal>
  </>;
}
