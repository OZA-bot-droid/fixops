import { useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useOrganization } from "./OrganizationProvider";
import {useOnboardingAccess} from "./OnboardingAccessProvider";
import { searchComplexes, registerComplex, getKaptComplex, getKaptCatalogStatus } from "./complexApi";
import { explainError } from "../../api/errors";
import { Alert, Button, Card, Field, Modal } from "../../components/ui";
import { PageHeader } from "../../components/layout/AppLayout";
import KaptRegionSelector from "./KaptRegionSelector";

const SIZE = 20;
const initialForm = { kaptCode: "", displayName: "", roadAddress: "" };
export default function ComplexRegistrationPage() {
  const {activeOrganizationId,reloadComplexes}=useOrganization();
  const {eligible,error:applicationsError,loading:applicationsLoading}=useOnboardingAccess();
  const [params] = useSearchParams();
  const [orgId, setOrgId] = useState(() => params.get("organizationId") || "");
  const [region, setRegion] = useState("");
  const [query, setQuery] = useState("");
  const [coverage, setCoverage] = useState(null);
  const [coverageError, setCoverageError] = useState(false);
  const [coverageRefresh, setCoverageRefresh] = useState(0);
  const [searchValidation, setSearchValidation] = useState("");
  const [search, setSearch] = useState(null);
  const [results, setResults] = useState({ items: [], totalCount: 0, loading: false, error: "" });
  const [form, setForm] = useState(initialForm);
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");
  const detailRequest = useRef(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailFailed, setDetailFailed] = useState(false);
  const pending = useRef(false);
  const mounted = useRef(false);
  useEffect(() => {
    mounted.current = true;
    return ()=>{mounted.current=false;detailRequest.current?.abort();};
  },[]);
  const selectedOrg = orgId ? eligible.get(orgId) : eligible.get(String(activeOrganizationId)) || (eligible.size === 1 ? [...eligible.values()][0] : null);
  useEffect(() => {
    const controller = new AbortController();
    setCoverage(null); setCoverageError(false);
    getKaptCatalogStatus(controller.signal)
      .then(data => { if (!controller.signal.aborted) setCoverage(data); })
      .catch(() => { if (!controller.signal.aborted) setCoverageError(true); });
    return () => controller.abort();
  }, [coverageRefresh]);
  useEffect(() => {
    if (!search) return;
    const controller = new AbortController();
    setResults({ items: [], totalCount: 0, loading: true, error: "" });
    searchComplexes(search.region, search.query, search.page, SIZE, controller.signal)
      .then(data => { if (!controller.signal.aborted) setResults({ items: data.items || [], totalCount: data.totalCount || 0, loading: false, error: "" }); })
      .catch(e => { if (!controller.signal.aborted) setResults({ items: [], totalCount: 0, loading: false, error: !search.region && e?.response?.data?.error?.code === "DEPENDENCY_UNAVAILABLE" ? "전국 검색 데이터를 사용할 수 없습니다. 동기화 상태를 갱신하거나 지역을 선택해 주세요." : explainError(e) }); });
    return () => controller.abort();
  }, [search]);
  function submitSearch(e) {
    e.preventDefault();
    setSearchValidation("");
    if (!region.trim() && query.replace(/\s+/g, "").length < 2) {
      setSearchValidation("전국 검색은 단지명을 두 글자 이상 입력해 주세요."); return;
    }
    if (!region.trim() && !coverage?.nationwideReady) {
      setSearchValidation("전국 동기화가 완료되지 않았습니다. 지역을 선택해 검색해 주세요."); return;
    }
    setSearch({ region: region.trim(), query: query.trim(), page: 1 });
  }
  function resetDetail() {
    detailRequest.current?.abort();
    detailRequest.current = null;
    setDetailLoading(false);
    setDetailFailed(false);
  }
  async function choose(item) {
    resetDetail();
    const controller = new AbortController();
    detailRequest.current = controller;
    setDetailLoading(true);
    setForm({ kaptCode: item.kaptCode, displayName: item.kaptName, roadAddress: "" });
    setError(""); setNotice("");
    try {
      const detail = await getKaptComplex(item.kaptCode, controller.signal);
      if (!controller.signal.aborted && mounted.current) {
        setForm({ kaptCode: detail.kaptCode, displayName: detail.kaptName, roadAddress: detail.roadAddress });
      }
    } catch (e) {
      if (!controller.signal.aborted && mounted.current) { setDetailFailed(true); setError(explainError(e)); }
    } finally {
      if (!controller.signal.aborted && mounted.current) setDetailLoading(false);
    }
  }
  async function register() {
    if (detailLoading || detailFailed || !selectedOrg || pending.current || !form.displayName.trim() || !form.roadAddress.trim()) return;
    pending.current = true; setBusy(true); setError(""); setNotice("");
    try {
      const data = await registerComplex(selectedOrg.id, { kaptCode: form.kaptCode, displayName: form.displayName.trim(), roadAddress: form.roadAddress.trim() });
      if (!mounted.current) return;
      setNotice("단지 관리권 신청이 접수되었습니다. 신청 #" + data.managementId + " · 플랫폼 관리자 검토 대기");
      resetDetail(); setForm(initialForm); setConfirming(false);
      if (String(selectedOrg.id) === String(activeOrganizationId)) reloadComplexes();
    } catch (e) { if (mounted.current) setError(explainError(e)); }
    finally { pending.current = false; if (mounted.current) setBusy(false); }
  }
  return <>
    <PageHeader title="단지 등록·관리권 신청" description="K-apt 단지를 검색하거나 직접 등록하여 플랫폼 관리자의 관리권 검토를 요청하세요." />
    <div className="grid gap-6">
      <Card className="grid gap-3 p-6">
        <Field label="신청 조직" as="select" disabled={busy} value={selectedOrg ? String(selectedOrg.id) : ""} onChange={e => { resetDetail(); setOrgId(e.target.value); setForm(initialForm); setNotice(""); setError(""); }}>
          <option value="">조직 선택</option>
          {[...eligible.values()].map(o => <option key={o.id} value={String(o.id)}>{o.name} · {o.status === "ACTIVE" ? "운영 중" : "개설 승인 대기"}</option>)}
        </Field>
        {applicationsLoading && <p role="status">신청 조직 확인 중…</p>}
        {applicationsError && <Alert tone="error">{applicationsError}</Alert>}
        {!applicationsLoading && orgId && !eligible.has(orgId) && <Alert tone="error">요청한 조직은 등록 가능한 조직이 아닙니다. 신청 조직을 다시 선택해 주세요.</Alert>}
        {!applicationsLoading && !eligible.size && <Alert>먼저 공동주택 조직 개설을 신청하거나 기존 공동주택 조직의 ADMIN 권한을 확보해 주세요.</Alert>}
      </Card>
      <Card className="grid gap-4 p-6">
        <h2 className="font-bold">K-apt 단지 검색</h2>
        <form className="grid gap-3 sm:grid-cols-2" onSubmit={submitSearch}>
          <KaptRegionSelector value={region} onChange={setRegion} disabled={busy || results.loading} />
          <Field label={region ? "단지명 (선택)" : "단지명 (전국 검색 필수)"} value={query} maxLength={100} onChange={e => setQuery(e.target.value)} />
          <Button type="submit" disabled={busy || results.loading}>검색</Button>
        </form>
        <p className="text-xs text-fixops-muted">지역을 선택하지 않으면 전국 단지명을 검색합니다. 전국 검색은 모든 시·도 데이터가 준비된 경우에만 가능합니다.</p>
        {!region && <div className="flex flex-wrap items-center gap-2">
          <p role="status" className="text-sm">{coverage ? (coverage.nationwideReady ? "전국 검색 가능" : `전국 데이터 준비 중 (${coverage.readyCount}/${coverage.totalCount})`) : coverageError ? "동기화 상태 조회 실패" : "동기화 상태 확인 중…"}</p>
          <Button type="button" variant="secondary" disabled={busy || results.loading} onClick={() => setCoverageRefresh(v => v + 1)}>상태 새로고침</Button>
        </div>}
        {searchValidation && <Alert tone="error">{searchValidation}</Alert>}
        {results.loading && <p role="status">조회 중…</p>}
        {results.error && <Alert tone="error">{results.error}</Alert>}
        {search && !results.loading && !results.error && !results.items.length && <p>일치하는 단지가 없습니다. 검색어 또는 지역을 변경하거나 직접 등록해 주세요.</p>}
        {results.items.map(item => <div key={item.kaptCode} className="flex flex-wrap items-center justify-between gap-3 border-t border-fixops-border py-3">
          <div><p className="font-semibold">{item.kaptName}</p><p className="text-sm text-fixops-muted">{item.kaptCode} · {item.regionName || item.roadAddress || "주소 확인 필요"}</p></div>
          <Button variant="secondary" disabled={busy || !selectedOrg} onClick={() => choose(item)}>선택</Button>
        </div>)}
        {search && <div className="flex items-center justify-between"><Button variant="secondary" disabled={results.loading || busy || search.page <= 1} onClick={() => setSearch(s => ({ ...s, page: s.page - 1 }))}>이전</Button><span>{search.page} 페이지 · 총 {results.totalCount}개</span><Button variant="secondary" disabled={results.loading || busy || search.page * SIZE >= results.totalCount} onClick={() => setSearch(s => ({ ...s, page: s.page + 1 }))}>다음</Button></div>}
      </Card>
      <Card className="grid gap-4 p-6">
        <div className="flex items-center justify-between"><h2 className="font-bold">단지 등록 정보</h2><Button variant="secondary" disabled={busy} onClick={() => { resetDetail(); setForm(initialForm); setError(""); }}>직접 등록</Button></div>
        <form className="grid gap-4" onSubmit={e => { e.preventDefault(); if (busy || detailLoading || detailFailed || !selectedOrg) return; setError(""); setConfirming(true); }}>
          {detailLoading && <p role="status">공동주택 상세정보 조회 중…</p>}
          {detailFailed && <><Alert tone="error">공식 단지 정보를 조회하지 못했습니다. 재조회하거나 직접 등록으로 전환해 주세요.</Alert><Button type="button" variant="secondary" disabled={busy || detailLoading} onClick={() => choose({ kaptCode: form.kaptCode, kaptName: form.displayName })}>상세정보 재조회</Button></>}
          {form.kaptCode && <Field label="K-apt 코드" value={form.kaptCode} readOnly />}
          <Field label="단지명" required maxLength={200} disabled={busy || detailLoading} value={form.displayName} onChange={e => setForm(f => ({ ...f, displayName: e.target.value }))} />
          <Field label="도로명 주소" required maxLength={500} disabled={busy || detailLoading} value={form.roadAddress} onChange={e => setForm(f => ({ ...f, roadAddress: e.target.value }))} />
          <Alert>{form.kaptCode ? "서버가 K-apt 상세 정보를 재조회합니다. 공식 정보가 확인되지 않으면 수동 심사 대상으로 등록됩니다." : "직접 등록된 단지는 수동 심사 대상입니다. 플랫폼 관리자가 동일 주소 후보와 관리권을 확인합니다."}</Alert>
          {notice && <><Alert tone="success">{notice}</Alert><Link to="/complexes/manage" className="text-sm font-semibold text-fixops-primary underline">관리권 신청 내역 확인</Link></>}
          {error && <Alert tone="error">{error}</Alert>}
          <Button type="submit" disabled={busy || detailLoading || detailFailed || !selectedOrg || !form.displayName.trim() || !form.roadAddress.trim()}>관리권 신청</Button>
        </form>
      </Card>
    </div>
    <Modal open={confirming} title="단지 관리권 신청 확인" description={selectedOrg?.name} onClose={() => { if (!pending.current) setConfirming(false); }} footer={<><Button variant="secondary" disabled={busy} onClick={() => setConfirming(false)}>취소</Button><Button disabled={busy} onClick={register}>{busy ? "신청 중…" : "신청 확인"}</Button></>}>
      <p className="font-semibold">{form.displayName}</p><p className="text-sm">{form.roadAddress}</p>
      {error && <Alert tone="error">{error}</Alert>}
    </Modal>
  </>;
}
