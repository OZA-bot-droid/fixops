import { Link } from "react-router-dom";
import { useEffect, useRef, useState } from "react";
import { useOrganization } from "./OrganizationProvider";
import { uploadRoster, previewRoster, commitRoster } from "./rosterApi";
import { explainError, errorCode } from "../../api/errors";
import { Alert, Button, Card, Field, Modal, StatusBadge } from "../../components/ui";
import { PageHeader } from "../../components/layout/AppLayout";

const SIZE = 20;
const labels = { NEW: "신규", CHANGED: "정보 변경", UNCHANGED: "변경 없음", EXPLICIT_MOVE_OUT: "명시적 퇴거" };
const errors = { INVALID_ROW: "필수 항목 또는 날짜 오류", INVALID_CONTACT: "연락처 형식 오류", DUPLICATE_ROW: "중복 입주자 행" };
function ImportJob({ complexId, initial }) {
  const [job, setJob] = useState(initial);
  const [page, setPage] = useState(0);
  const [revision, setRevision] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [ack, setAck] = useState(false);
  const [reviewedPages, setReviewedPages] = useState([]);
  const reviewSignature = useRef(null);
  const [moveAck, setMoveAck] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const pending = useRef(false);
  const mounted = useRef(false);
  const jobId = initial.id;
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true); setError(""); setAck(false); setMoveAck(false);
    previewRoster(complexId, jobId, page, SIZE, controller.signal)
      .then(data => {
        if (controller.signal.aborted) return;
        const signature = JSON.stringify([data.id, data.status, data.base_version, data.row_count, data.missingCount, data.changes]);
        const expected = Math.max(0, Math.min(SIZE, Number(data.missingCount || 0) - page * SIZE));
        const validPage = expected > 0 && Array.isArray(data.missingRows) && data.missingRows.length === expected;
        const changed = reviewSignature.current !== signature;
        reviewSignature.current = signature;
        setReviewedPages(old => {
          const previous = changed ? [] : old;
          return validPage && !previous.includes(page) ? [...previous, page] : previous;
        });
        setJob(data);
      })
      .catch(e => { if (!controller.signal.aborted) setError(explainError(e)); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [complexId, jobId, page, revision]);
  const counts = Object.fromEntries((job.changes || []).map(x => [x.change_type, Number(x.count)]));
  const missing = Number(job.missingCount || 0);
  const moveOut = Number(counts.EXPLICIT_MOVE_OUT || 0);
  const missingPages = Math.ceil(missing / SIZE);
  const allMissingReviewed = Array.from({ length: missingPages }, (_, i) => i).every(i => reviewedPages.includes(i));
  const canCommit = !loading && !error && !busy && job.status === "PREVIEW" &&
    (job.mode !== "FULL" || missing === 0 || (allMissingReviewed && ack)) && (moveOut === 0 || moveAck);
  const pages = Math.max(1, Math.ceil(Math.max(Number(job.row_count || 0), missing) / SIZE));
  async function commit() {
    if (!canCommit || pending.current) return;
    pending.current = true; setBusy(true); setError("");
    try {
      const result = await commitRoster(complexId, jobId, job.mode === "FULL" && missing > 0 && ack);
      if (!mounted.current) return;
      if (result.status !== "COMPLETE") throw new Error("INVALID_RESPONSE");
      setJob(old => ({ ...old, ...result, rows: [], missingRows: [], changes: [] }));
      setConfirming(false);
    } catch (e) {
      if (mounted.current) {
        setConfirming(false);
        setError(errorCode(e) === "ROSTER_IMPORT_CONFLICT" ? "명부 버전 또는 누락 상태가 변경되었습니다. 최신 상태를 확인하고 원본 파일을 다시 업로드해 주세요." : explainError(e));
      }
    } finally { pending.current = false; if (mounted.current) setBusy(false); }
  }
  return <Card className="grid gap-5 p-6">
    <div className="flex flex-wrap items-center justify-between gap-2"><h2 className="font-bold">검증 결과 · 작업 #{jobId}</h2><StatusBadge status={job.status} /></div>
    <p className="text-sm text-fixops-muted">유형 {job.mode} · 전체 {job.row_count}행 · 기준 버전 {job.base_version}</p>
    {job.status === "COMPLETE" ? <Alert tone="success">명부 반영이 완료되었습니다. {job.version != null ? "버전 " + job.version : ""}</Alert> : <>
      {loading && <p role="status">검증 결과 조회 중…</p>}
      {error && <><Alert tone="error">{error}</Alert><Button variant="secondary" disabled={busy || loading} onClick={() => { setReviewedPages([]); reviewSignature.current = null; setRevision(v => v + 1); }}>상태 다시 조회</Button></>}
      {job.status === "INVALID" && <Alert tone="error">유효성 오류가 있는 명부는 확정할 수 없습니다. 원본의 오류 행을 수정해 다시 업로드해 주세요.</Alert>}
      {job.status === "PREVIEW" && <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {Object.entries(labels).map(([key, label]) => <div key={key} className="rounded-lg border border-fixops-border p-4"><p className="text-xs text-fixops-muted">{label}</p><p className="mt-2 text-2xl font-bold">{counts[key] || 0}</p></div>)}
      </div>}
      {!loading && <div className="overflow-x-auto"><table className="w-full text-left text-sm"><thead><tr>{["행","동","호","입주 상태","검증 결과"].map(x => <th key={x} className="p-2">{x}</th>)}</tr></thead><tbody>
        {(job.rows || []).map(row => <tr key={row.row_no} className="border-t border-fixops-border"><td className="p-2">{row.row_no}</td><td className="p-2">{row.building_no || "—"}</td><td className="p-2">{row.unit_no || "—"}</td><td className="p-2">{row.occupancy_status === "MOVED_OUT" ? "퇴거" : row.occupancy_status === "ACTIVE" ? "입주" : "—"}</td><td className="p-2">{row.validation_status === "VALID" ? "정상" : errors[row.error_code] || row.error_code || "검증 실패"}</td></tr>)}
      </tbody></table></div>}
      {job.status === "PREVIEW" && job.mode === "FULL" && <div className="grid gap-3 rounded-xl border border-fixops-border p-4">
        <h3 className="font-semibold">기존 명부 누락 대상 · {missing}건</h3>
        <Alert>누락된 기존 입주자는 자동 퇴거 처리되지 않으며 기존 상태를 유지합니다.</Alert>
        {!loading && (job.missingRows || []).map(row => <div key={row.id} className="flex justify-between border-t border-fixops-border py-2 text-sm"><span>{row.building_no}동 {row.unit_no}호 · #{row.id}</span><span>기존 유지</span></div>)}
        {missing > 0 && <p className="text-sm text-fixops-muted">누락 목록 조회 완료: {reviewedPages.filter(p => p < missingPages).length} / {missingPages} 페이지. 모든 누락 페이지를 확인한 후 확정하세요.</p>}
        {missing > 0 && <label className="flex items-start gap-2 text-sm"><input type="checkbox" checked={ack} disabled={loading || busy || !!error || !allMissingReviewed} onChange={e => setAck(e.target.checked)} /> 누락 대상 {missing}건을 확인했으며 기존 상태 유지에 동의합니다.</label>}
      </div>}
      <div className="flex justify-between gap-2"><Button variant="secondary" disabled={loading || busy || page === 0} onClick={() => setPage(p => p - 1)}>이전</Button><span>{page + 1} / {pages} 페이지</span><Button variant="secondary" disabled={loading || busy || page + 1 >= pages} onClick={() => setPage(p => p + 1)}>다음</Button></div>
      {job.status === "PREVIEW" && moveOut > 0 && <label className="flex items-start gap-2 text-sm"><input type="checkbox" checked={moveAck} disabled={busy || loading || !!error} onChange={e => setMoveAck(e.target.checked)} /> 명시적 퇴거 {moveOut}건의 기존 세대 접근 권한이 회수될 수 있음을 확인합니다.</label>}
      {job.status === "PREVIEW" && <Button disabled={!canCommit} onClick={() => setConfirming(true)}>명부 최종 반영</Button>}
    </>}
    <Modal open={confirming} title="명부 최종 반영" onClose={() => { if (!pending.current) setConfirming(false); }} footer={<><Button variant="secondary" disabled={busy} onClick={() => setConfirming(false)}>취소</Button><Button disabled={!canCommit} onClick={commit}>{busy ? "반영 중…" : "확정"}</Button></>}>
      <p className="text-sm">선택 단지의 명부에 검증된 데이터를 반영합니다. 명시적 퇴거 {moveOut}건, 기존 유지 대상 {missing}건입니다.</p>
    </Modal>
  </Card>;
}

function RosterImport({ complexId }) {
  const [mode, setMode] = useState("PARTIAL");
  const [file, setFile] = useState(null);
  const [job, setJob] = useState(null);
  const [revision, setRevision] = useState(0);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const pending = useRef(false);
  const mounted = useRef(false);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  function selectFile(e) {
    const selected = e.target.files?.[0];
    setError(""); setFile(null); setJob(null);
    if (!selected) return;
    if (!/\.xlsx$/i.test(selected.name) || selected.size === 0 || selected.size > 5 * 1024 * 1024) {
      setError("비어 있지 않은 5MB 이하 XLSX 파일을 선택해 주세요."); e.target.value = ""; return;
    }
    setFile(selected);
  }
  async function upload(e) {
    e.preventDefault();
    if (!file || pending.current) return;
    pending.current = true; setBusy(true); setError(""); setJob(null);
    try {
      const data = await uploadRoster(complexId, mode, file);
      if (!mounted.current) return;
      if (data?.id == null) throw new Error("INVALID_RESPONSE");
      setJob(data); setRevision(v => v + 1);
    } catch (e) { if (mounted.current) setError(explainError(e)); }
    finally { pending.current = false; if (mounted.current) setBusy(false); }
  }
  return <div className="grid gap-6">
    <Card className="grid gap-4 p-6">
      <h2 className="font-bold">입주자 명부 업로드</h2>
      <form className="grid gap-4" onSubmit={upload}>
        <Field label="업로드 유형" as="select" value={mode} disabled={busy} onChange={e => { setMode(e.target.value); setJob(null); }}><option value="PARTIAL">부분 명부 (PARTIAL)</option><option value="FULL">전체 명부 (FULL)</option></Field>
        <Alert>{mode === "FULL" ? "전체 명부를 비교합니다. 누락된 기존 입주자는 자동 퇴거되지 않습니다." : "파일에 포함된 행만 추가하거나 변경합니다."}</Alert>
        <Field label="XLSX 파일" type="file" accept=".xlsx" disabled={busy} onChange={selectFile} required />
        <p className="text-sm text-fixops-muted">5MB 이하 · 최대 2,000행 · 시트 1개 · 동, 호, 성명, 연락처, 입주상태 필수 · 입주일, 퇴거일 선택</p>
        {error && <Alert tone="error">{error}</Alert>}
        <Button type="submit" disabled={busy || !file}>{busy ? "검증 중…" : "업로드 및 검증"}</Button>
      </form>
    </Card>
    {job && <ImportJob key={job.id + ":" + revision} complexId={complexId} initial={job} />}
  </div>;
}
export default function RosterManagementPage() {
  const { activeOrganizationKind, activeOrganizationId, activeOrganizationName, activeComplexId, complexes } = useOrganization();
  const complex = complexes.find(c => String(c.id) === String(activeComplexId));
  return <>
    <PageHeader title="입주자 명부 관리" description={activeOrganizationName || "업무 조직"} />
    {activeOrganizationKind !== "BUILDING" || !complex || complex.access_scope !== "ADMIN"
      ? <Alert>관리 권한이 있는 공동주택 단지를 선택해 주세요.</Alert>
      : <><div className="mb-4 flex flex-wrap items-center justify-between gap-3"><p className="font-semibold">{complex.display_name}</p><Link className="rounded-lg border border-fixops-border px-4 py-3 text-sm font-semibold text-fixops-primary" to="/residents/registration-codes">입주자 등록 코드 관리</Link></div><RosterImport key={activeOrganizationId + ":" + activeComplexId} complexId={activeComplexId} /></>}
  </>;
}
