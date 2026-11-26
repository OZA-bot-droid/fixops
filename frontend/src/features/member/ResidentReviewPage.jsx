import { useEffect, useRef, useState } from "react";
import { useOrganization } from "./OrganizationProvider";
import { pendingResidentEnrollments, decideResident } from "./residentApi";
import { explainError, errorCode } from "../../api/errors";
import { Alert, Button, Card, Modal, StatusBadge } from "../../components/ui";
import { PageHeader } from "../../components/layout/AppLayout";

const SIZE = 20;
function ReviewList({ complexId }) {
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
    pendingResidentEnrollments(complexId, page, SIZE, controller.signal)
      .then(items => {
        if (!Array.isArray(items)) throw new Error("INVALID_RESPONSE");
        if (!controller.signal.aborted) setResult({ items, loading: false, error: "" });
      }).catch(e => { if (!controller.signal.aborted) setResult({ items: [], loading: false, error: explainError(e) }); });
    return () => controller.abort();
  }, [complexId, page, revision]);
  function open(item, approve) {
    if (busy || (approve && (Number(item.match_count) !== 1 || !item.verified_at))) return;
    setError(""); setNotice(""); setSelected({ item, approve });
  }
  async function confirm() {
    if (!selected || pending.current) return;
    if (selected.approve && (Number(selected.item.match_count) !== 1 || !selected.item.verified_at)) return;
    pending.current = true; setBusy(true); setError("");
    try {
      const data = await decideResident(selected.item.id, selected.approve);
      if (!mounted.current) return;
      if (data?.status !== (selected.approve ? "APPROVED" : "REJECTED")) throw new Error("INVALID_RESPONSE");
      setNotice(selected.approve ? "주민 등록을 승인했습니다. 신청자는 다시 로그인해야 합니다." : "주민 등록을 반려했습니다.");
      setSelected(null); setPage(0); setRevision(v => v + 1);
    } catch (e) {
      if (mounted.current) setError(errorCode(e) === "INVALID_STATE_TRANSITION"
        ? "승인 조건을 충족하지 못했거나 신청 상태가 변경되었습니다. 명부 일치, 기존 세대 연결, 회원 역할 및 Asset 세대 등록 상태를 확인해 주세요."
        : explainError(e));
    } finally { pending.current = false; if (mounted.current) setBusy(false); }
  }
  return <Card className="grid gap-4 p-6">
    <div className="flex flex-wrap items-center justify-between gap-3"><h2 className="font-bold">주민 등록 심사 대기</h2><Button variant="secondary" disabled={busy || result.loading} onClick={() => { setNotice(""); setRevision(v => v + 1); }}>새로고침</Button></div>
    <Alert>등록 코드 또는 연락처 OTP 인증과 활성 명부의 정확한 일치가 필수입니다. 승인 시 서버에서 실제 세대 위치와 기존 연결 여부를 다시 확인합니다.</Alert>
    {notice && <Alert tone="success">{notice}</Alert>}
    {result.loading && <p role="status">신청 내역 조회 중…</p>}
    {result.error && <Alert tone="error">{result.error}</Alert>}
    {!result.loading && !result.error && !result.items.length && <p>심사 대기 신청이 없습니다.</p>}
    {result.items.map(item => {
      const matched = Number(item.match_count) === 1;
      const verified = Boolean(item.verified_at);
      return <div key={item.id} className="grid gap-3 border-t border-fixops-border py-4">
        <div className="flex flex-wrap items-center justify-between gap-2"><p className="font-semibold">{item.building_no}동 {item.unit_no}호</p><StatusBadge status={item.status} /></div>
        <p className="text-xs text-fixops-muted">신청 #{item.id} · 회원 #{item.member_id}</p>
        <p className="text-sm">{item.verification_method === "REGISTRATION_CODE" ? "등록 코드" : "연락처 OTP"}: {verified ? "인증 완료" : "미인증"} · 명부 일치: {item.match_count}건</p>
        {!matched && <Alert tone="error">명부 일치 건수가 1건이 아닙니다. 명부를 확인한 후 다시 심사해 주세요.</Alert>}
        <div className="flex gap-2"><Button disabled={busy || !matched || !verified} onClick={() => open(item, true)}>승인</Button><Button variant="secondary" disabled={busy} onClick={() => open(item, false)}>반려</Button></div>
      </div>;
    })}
    <div className="flex items-center justify-between"><Button variant="secondary" disabled={busy || result.loading || page === 0} onClick={() => setPage(p => p - 1)}>이전</Button><span>{page + 1} 페이지</span><Button variant="secondary" disabled={busy || result.loading || result.items.length < SIZE} onClick={() => setPage(p => p + 1)}>다음</Button></div>
    <Modal open={!!selected} title={selected?.approve ? "주민 등록 승인" : "주민 등록 반려"} onClose={() => { if (!pending.current) setSelected(null); }} footer={<><Button variant="secondary" disabled={busy} onClick={() => setSelected(null)}>취소</Button><Button variant={selected?.approve ? "primary" : "danger"} disabled={busy} onClick={confirm}>{busy ? "처리 중…" : "결정 확인"}</Button></>}>
      {selected && <p className="mb-3 text-sm">{selected.item.building_no}동 {selected.item.unit_no}호 · 신청 #{selected.item.id}</p>}
      <Alert>{selected?.approve ? "승인하면 조직 USER 및 해당 세대 접근 권한이 부여됩니다. 서버의 최종 검증에 실패하면 승인되지 않습니다." : "주민 등록 신청을 반려합니다. 기존 명부나 세대 권한은 변경하지 않습니다."}</Alert>
      {error && <div className="mt-3"><Alert tone="error">{error}</Alert></div>}
    </Modal>
  </Card>;
}
export default function ResidentReviewPage() {
  const { activeOrganizationKind, activeOrganizationId, activeOrganizationName, activeComplexId, complexes } = useOrganization();
  const complex = complexes.find(c => String(c.id) === String(activeComplexId));
  return <>
    <PageHeader title="주민 등록 심사" description={activeOrganizationName || "업무 조직"} />
    {activeOrganizationKind !== "BUILDING" || !complex || complex.access_scope !== "ADMIN"
      ? <Alert>관리 권한이 있는 공동주택 단지를 선택해 주세요.</Alert>
      : <><p className="mb-4 font-semibold">{complex.display_name}</p><ReviewList key={activeOrganizationId + ":" + activeComplexId} complexId={activeComplexId} /></>}
  </>;
}
