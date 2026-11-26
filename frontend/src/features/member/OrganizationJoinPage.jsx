import { canApplyOrganization } from "./onboardingPolicy";
import { MyOnboardingInvitations } from "./OnboardingInvitations";
import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "./AuthProvider";
import { useOrganization } from "./OrganizationProvider";
import { searchOrganizations, requestJoin, myJoinRequests, acceptInvitation } from "./organizationApi";
import { explainError } from "../../api/errors";
import { Alert, Button, Card, Field, StatusBadge, Modal } from "../../components/ui";
import { PageHeader } from "../../components/layout/AppLayout";

const SIZE = 20;
export default function OrganizationJoinPage() {
  const { clearAuth, user } = useAuth();
  const { memberships } = useOrganization();
  const [query, setQuery] = useState("");
  const [search, setSearch] = useState({ q: "", page: 0, revision: 0 });
  const [result, setResult] = useState({ items: [], loading: true, error: "" });
  const [history, setHistory] = useState(null);
  const [historyPage, setHistoryPage] = useState(0);
  const [historyResult, setHistoryResult] = useState({ items: [], loading: false, error: "" });
  const [selected, setSelected] = useState(null);
  const [token, setToken] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const pending = useRef(false);
  const [requested, setRequested] = useState({});
  useEffect(() => {
    const controller = new AbortController();
    setResult({ items: [], loading: true, error: "" });
    searchOrganizations(search.q, search.page, SIZE, controller.signal)
      .then(items => { if (!controller.signal.aborted) setResult({ items, loading: false, error: "" }); })
      .catch(e => { if (!controller.signal.aborted) setResult({ items: [], loading: false, error: explainError(e) }); });
    return () => controller.abort();
  }, [search]);
  useEffect(() => {
    if (!history) return;
    const controller = new AbortController();
    setHistoryResult({ items: [], loading: true, error: "" });
    myJoinRequests(history.id, historyPage, SIZE, controller.signal)
      .then(items => { if (!controller.signal.aborted) setHistoryResult({ items, loading: false, error: "" }); })
      .catch(e => { if (!controller.signal.aborted) setHistoryResult({ items: [], loading: false, error: explainError(e) }); });
    return () => controller.abort();
  }, [history, historyPage]);
  async function mutate(task) {
    if (pending.current) return;
    pending.current = true; setBusy(true); setError(""); setNotice("");
    try { await task(); } catch (e) { setError(explainError(e)); }
    finally { pending.current = false; setBusy(false); }
  }
  function showHistory(org) { setHistoryPage(0); setHistory({ ...org }); }
  return <>
    <PageHeader title="조직 가입·초대 수락" description="활성 조직을 검색하고 가입을 신청하거나 초대 코드를 수락하세요." />
    {notice && <div className="mb-4"><Alert tone="success">{notice}</Alert></div>}
    {error && <div className="mb-4"><Alert tone="error">{error}</Alert></div>}
    <div className="grid items-start gap-6 xl:grid-cols-[2fr_1fr]">
      <Card className="grid gap-5 p-6">
        <form className="flex items-end gap-3" onSubmit={e => { e.preventDefault(); setSearch(s => ({ q: query.trim(), page: 0, revision: s.revision + 1 })); }}>
          <div className="flex-1"><Field label="조직명 검색" value={query} maxLength={200} onChange={e => setQuery(e.target.value)} /></div>
          <Button type="submit" disabled={busy}>검색</Button>
        </form>
        {result.loading && <p role="status">조직 조회 중…</p>}
        {result.error && <Alert tone="error">{result.error}</Alert>}
        {!result.loading && !result.error && !result.items.length && <p>검색 결과가 없습니다.</p>}
        {result.items.map(org => {
          const joined = memberships.some(m => String(m.organizationId) === String(org.id));
          return <div key={org.id} className="flex flex-wrap items-center justify-between gap-3 border-t border-fixops-border py-4">
            <div><p className="font-semibold">{org.name}</p><p className="text-xs text-fixops-muted">{org.kind === "BUILDING" ? "공동주택" : "공장"}</p></div>
            <div className="flex flex-wrap gap-2">
              <Button variant="secondary" disabled={busy} onClick={() => showHistory(org)}>내 신청 내역</Button>
              <Button disabled={busy || joined || requested[org.id] === "PENDING_APPROVAL"} onClick={() => setSelected(org)}>{joined ? "가입 완료" : requested[org.id] === "PENDING_APPROVAL" ? "승인 대기" : "가입 신청"}</Button>
            </div>
          </div>;
        })}
        <div className="flex items-center justify-between">
          <Button variant="secondary" disabled={result.loading || busy || search.page === 0} onClick={() => setSearch(s => ({ ...s, page: s.page - 1 }))}>이전</Button>
          <span>{search.page + 1} 페이지</span>
          <Button variant="secondary" disabled={result.loading || busy || result.items.length < SIZE} onClick={() => setSearch(s => ({ ...s, page: s.page + 1 }))}>다음</Button>
        </div>
      </Card>
      <Card className="grid gap-4 p-6">
        <h2 className="font-bold">초대 수락</h2>
        <p className="text-sm text-fixops-muted">관리자에게 전달받은 초대 코드를 입력하세요. 수락 후 권한 갱신을 위해 다시 로그인합니다.</p>
        <form className="grid gap-3" onSubmit={e => { e.preventDefault(); mutate(async () => { await acceptInvitation(token.trim()); setToken(""); clearAuth("초대 수락이 완료되었습니다. 새 권한을 적용하려면 다시 로그인해 주세요."); }); }}>
          <Field label="초대 코드" type="password" autoComplete="off" required maxLength={128} value={token} onChange={e => setToken(e.target.value)} />
          <Button type="submit" disabled={busy || !token.trim()}>초대 수락</Button>
        </form>
        {canApplyOrganization(user) && <Link to="/organizations/apply" className="text-sm text-fixops-primary">서비스 도입 신청</Link>}
      </Card>
    </div>
    <div className="mt-6"><MyOnboardingInvitations /></div>
    <Modal open={!!selected} title="조직 가입 신청" description={selected?.name} onClose={() => { if (!busy) setSelected(null); }}
      footer={<Button disabled={busy} onClick={() => mutate(async () => { const org = selected; const data = await requestJoin(org.id); setRequested(v => ({ ...v, [org.id]: data.status })); setSelected(null); setNotice("가입 신청을 접수했습니다. 관리자 심사를 기다려 주세요."); showHistory(org); })}>신청 확인</Button>}>
      <p>가입 승인 후 조직 USER 권한이 부여됩니다.</p>
      {error && <Alert tone="error">{error}</Alert>}
    </Modal>
    <Modal open={!!history} title="내 가입 신청 내역" description={history?.name} onClose={() => setHistory(null)}>
      {historyResult.loading && <p role="status">조회 중…</p>}
      {historyResult.error && <Alert tone="error">{historyResult.error}</Alert>}
      {!historyResult.loading && !historyResult.error && !historyResult.items.length && <p>신청 내역이 없습니다.</p>}
      {historyResult.items.map(item => <div key={item.id} className="flex items-center justify-between border-b border-fixops-border py-3"><span>신청 #{item.id}</span><StatusBadge status={item.status} /></div>)}
      <div className="mt-4 flex justify-between"><Button variant="secondary" disabled={historyResult.loading || historyPage === 0} onClick={() => setHistoryPage(p => p - 1)}>이전</Button><Button variant="secondary" disabled={historyResult.loading || historyResult.items.length < SIZE} onClick={() => setHistoryPage(p => p + 1)}>다음</Button></div>
    </Modal>
  </>;
}
