import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useOrganization } from "./OrganizationProvider";
import {useOnboardingAccess} from "./OnboardingAccessProvider";
import { listComplexManagements } from "./complexApi";
import { explainError } from "../../api/errors";
import { Alert, Button, Card, Field, StatusBadge } from "../../components/ui";
import { PageHeader } from "../../components/layout/AppLayout";

const SIZE = 20;
function ManagementHistory({ org }) {
  const [page, setPage] = useState(0);
  const [revision, setRevision] = useState(0);
  const [result, setResult] = useState({ items: [], loading: true, error: "" });
  useEffect(() => {
    const controller = new AbortController();
    setResult({ items: [], loading: true, error: "" });
    listComplexManagements(org.id, page, SIZE, controller.signal)
      .then(items => {
        if (!controller.signal.aborted) setResult({ items, loading: false, error: "" });
      }).catch(e => {
        if (!controller.signal.aborted) setResult({ items: [], loading: false, error: explainError(e) });
      });
    return () => controller.abort();
  }, [org.id, page, revision]);
  return <Card className="grid gap-4 p-6">
    <div className="flex flex-wrap items-center justify-between gap-2">
      <h2 className="font-bold">{org.name} · 단지 관리권 내역</h2>
      <Button variant="secondary" disabled={result.loading} onClick={() => setRevision(v => v + 1)}>새로고침</Button>
    </div>
    {result.loading && <p role="status">관리권 내역 조회 중…</p>}
    {result.error && <Alert tone="error">{result.error}</Alert>}
    {!result.loading && !result.error && !result.items.length && <p>등록된 단지 관리권 신청이 없습니다.</p>}
    {result.items.map(item => <div key={item.management_id} className="grid gap-2 border-t border-fixops-border py-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="font-semibold">{item.display_name}</p>
        <StatusBadge status={item.status} />
      </div>
      <p className="text-sm">{item.road_address}</p>
      <p className="text-xs text-fixops-muted">신청 #{item.management_id} · 단지 #{item.complex_id} · {item.kapt_code || "수동 등록"} · {item.verification_status}</p>
      {item.review_reason && <p className="break-words text-sm">심사 사유: {item.review_reason}</p>}
      {item.reviewed_at && <p className="text-xs text-fixops-muted">심사 일시: {new Date(item.reviewed_at).toLocaleString("ko-KR")}</p>}
    </div>)}
    <div className="flex items-center justify-between">
      <Button variant="secondary" disabled={result.loading || page === 0} onClick={() => setPage(p => p - 1)}>이전</Button>
      <span>{page + 1} 페이지</span>
      <Button variant="secondary" disabled={result.loading || result.items.length < SIZE} onClick={() => setPage(p => p + 1)}>다음</Button>
    </div>
  </Card>;
}
export default function ComplexManagementPage() {
  const {activeOrganizationId}=useOrganization();
  const {eligible,loading,error,reload}=useOnboardingAccess();
  const [selected,setSelected]=useState("");
  const org = selected ? eligible.get(selected) : eligible.get(String(activeOrganizationId)) || (eligible.size === 1 ? [...eligible.values()][0] : null);
  return <>
    <PageHeader title="단지 관리" description="조직별 단지 등록 신청 및 심사 결과를 확인합니다." action={eligible.size>0&&<Link className="font-semibold text-fixops-primary underline" to="/complexes/register">단지 등록 신청</Link>} />
    <div className="grid gap-5">
      <Card className="grid gap-3 p-6">
        <Field label="관리 조직" as="select" value={org ? String(org.id) : ""} onChange={e => setSelected(e.target.value)}>
          <option value="">조직 선택</option>
          {[...eligible.values()].map(o => <option key={o.id} value={String(o.id)}>{o.name}</option>)}
        </Field>
        {loading && <p role="status">신청 조직 조회 중…</p>}
        {error && <><Alert tone="error">{error}</Alert><Button variant="secondary" onClick={reload}>다시 조회</Button></>}
        {!loading && !eligible.size && <Alert>관리할 공동주택 조직이 없습니다. 먼저 조직을 개설해 주세요.</Alert>}
      </Card>
      {org && <ManagementHistory key={org.id} org={org} />}
    </div>
  </>;
}
