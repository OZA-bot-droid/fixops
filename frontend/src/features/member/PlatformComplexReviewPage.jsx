import { useEffect, useRef, useState } from "react";
import { pendingComplexes, addressCandidates, decideComplex } from "./complexApi";
import { explainError, errorCode } from "../../api/errors";
import { Alert, Button, Card, Field, Modal } from "../../components/ui";
import { PageHeader } from "../../components/layout/AppLayout";

const SIZE = 20;
function ReviewForm({ item, onDone, onClose }) {
  const [candidates, setCandidates] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [revision, setRevision] = useState(0);
  const [approve, setApprove] = useState(true);
  const [reason, setReason] = useState("");
  const [distinct, setDistinct] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const pending = useRef(false);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true); setLoadError(""); setCandidates([]); setDistinct(false);
    async function load() {
      const all = [];
      for (let page = 0; ; page++) {
        const rows = await addressCandidates(item.management_id, page, SIZE, controller.signal);
        if (controller.signal.aborted) return;
        if (!Array.isArray(rows)) throw new Error("INVALID_RESPONSE");
        all.push(...rows);
        if (rows.length < SIZE) break;
        if (page >= 49) throw new Error("CANDIDATE_LIMIT");
      }
      if (!controller.signal.aborted) setCandidates(all);
    }
    load().catch(e => { if (!controller.signal.aborted) setLoadError(e.message === "CANDIDATE_LIMIT" ? "후보가 너무 많아 전체 검토가 필요합니다." : explainError(e)); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [item.management_id, revision]);
  async function submit(e) {
    e.preventDefault();
    if (pending.current || !reason.trim() || (approve && (loading || loadError || (candidates.length && !distinct)))) return;
    pending.current = true; setBusy(true); setError("");
    try {
      await decideComplex(item.management_id, approve, reason.trim(), approve && candidates.length > 0 && distinct);
      onDone();
    } catch (e) {
      setError(explainError(e));
      if (errorCode(e) === "COMPLEX_ADDRESS_REVIEW_REQUIRED") setRevision(v => v + 1);
    } finally { pending.current = false; setBusy(false); }
  }
  return <Modal open title="단지 관리권 심사" description={item.display_name} onClose={() => { if (!pending.current) onClose(); }}>
    <form className="grid max-h-[65vh] gap-4 overflow-y-auto" onSubmit={submit}>
      <div className="text-sm"><p>조직 #{item.organization_id} · 신청 #{item.management_id}</p><p>{item.road_address}</p><p>K-apt: {item.kapt_code || "수동 등록"}</p></div>
      <Alert>관리권 증빙과 단지의 실제 동일성을 별도로 검토하세요. K-apt 검색 일치만으로 관리권이 입증되지는 않습니다.</Alert>
      <h3 className="font-semibold">동일 주소 후보</h3>
      {loading && <p role="status">후보 조회 중…</p>}
      {loadError && <><Alert tone="error">{loadError}</Alert><Button variant="secondary" disabled={busy} onClick={() => setRevision(v => v + 1)}>다시 조회</Button></>}
      {!loading && !loadError && !candidates.length && <p className="text-sm">동일 주소 후보가 없습니다.</p>}
      {candidates.map(c => <div key={c.id} className="rounded-lg border border-fixops-border p-3 text-sm"><p className="font-semibold">{c.display_name} · #{c.id}</p><p>{c.road_address}</p><p>{c.kapt_code || "수동 등록"} · {c.verification_status}</p></div>)}
      <Field label="심사 결과" as="select" disabled={busy} value={approve ? "approve" : "reject"} onChange={e => { setApprove(e.target.value === "approve"); setDistinct(false); }}><option value="approve">승인</option><option value="reject">반려</option></Field>
      {approve && !loading && !loadError && candidates.length > 0 && <label className="flex items-start gap-2 text-sm"><input type="checkbox" checked={distinct} disabled={busy} onChange={e => setDistinct(e.target.checked)} /> 모든 후보를 검토했으며 신청 단지가 기존 후보와 별개임을 확인합니다.</label>}
      <Field label="심사 사유" as="textarea" required maxLength={500} disabled={busy} value={reason} onChange={e => setReason(e.target.value)} />
      {error && <Alert tone="error">{error}</Alert>}
      <div className="flex justify-end gap-2"><Button variant="secondary" disabled={busy} onClick={onClose}>취소</Button><Button type="submit" variant={approve ? "primary" : "danger"} disabled={busy || !reason.trim() || (approve && (loading || !!loadError || (candidates.length > 0 && !distinct)))}>{busy ? "처리 중…" : approve ? "승인 확인" : "반려 확인"}</Button></div>
    </form>
  </Modal>;
}
export default function PlatformComplexReviewPage() {
  const [page, setPage] = useState(0);
  const [revision, setRevision] = useState(0);
  const [result, setResult] = useState({ items: [], loading: true, error: "" });
  const [selected, setSelected] = useState(null);
  const [notice, setNotice] = useState("");
  useEffect(() => {
    const controller = new AbortController();
    setResult({ items: [], loading: true, error: "" });
    pendingComplexes(page, SIZE, controller.signal).then(items => {
      if (!controller.signal.aborted) setResult({ items, loading: false, error: "" });
    }).catch(e => { if (!controller.signal.aborted) setResult({ items: [], loading: false, error: explainError(e) }); });
    return () => controller.abort();
  }, [page, revision]);
  return <>
    <PageHeader title="플랫폼 단지 관리권 심사" description="단지 정보, 관리권 및 동일 주소 후보를 검토합니다." />
    {notice && <div className="mb-4"><Alert tone="success">{notice}</Alert></div>}
    <Card className="grid gap-4 p-6">
      <div className="flex items-center justify-between"><h2 className="font-bold">심사 대기 단지</h2><Button variant="secondary" disabled={result.loading || !!selected} onClick={() => setRevision(v => v + 1)}>새로고침</Button></div>
      {result.loading && <p role="status">조회 중…</p>}
      {result.error && <Alert tone="error">{result.error}</Alert>}
      {!result.loading && !result.error && !result.items.length && <p>심사 대기 단지가 없습니다.</p>}
      {result.items.map(item => <div key={item.management_id} className="flex flex-wrap items-center justify-between gap-3 border-t border-fixops-border py-4"><div><p className="font-semibold">{item.display_name}</p><p className="text-sm">{item.road_address}</p><p className="text-xs text-fixops-muted">조직 #{item.organization_id} · {item.kapt_code || "수동 등록"}</p></div><Button onClick={() => { setNotice(""); setSelected(item); }}>심사</Button></div>)}
      <div className="flex items-center justify-between"><Button variant="secondary" disabled={result.loading || page === 0} onClick={() => setPage(p => p - 1)}>이전</Button><span>{page + 1} 페이지</span><Button variant="secondary" disabled={result.loading || result.items.length < SIZE} onClick={() => setPage(p => p + 1)}>다음</Button></div>
    </Card>
    {selected && <ReviewForm key={selected.management_id} item={selected} onClose={() => setSelected(null)} onDone={() => { setSelected(null); setNotice("심사 결과를 반영했습니다."); setRevision(v => v + 1); }} />}
  </>;
}
