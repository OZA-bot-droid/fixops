import { useEffect, useRef, useState } from "react";
import { useAuth } from "./AuthProvider";
import { getGeneration } from "../../api/authSession";
import { searchResidentComplexes, applyResident, verifyResident, myResidentEnrollments, redeemRegistrationCode, getResidentVerificationMethods } from "./residentApi";
import { explainError, errorCode } from "../../api/errors";
import { Alert, Button, Card, Field, StatusBadge } from "../../components/ui";
import { PageHeader } from "../../components/layout/AppLayout";

const SIZE = 20;
export default function ResidentEnrollmentPage() {
  const { clearAuth } = useAuth();
  const [query, setQuery] = useState("");
  const [search, setSearch] = useState({ q: "", page: 0 });
  const [result, setResult] = useState({ items: [], loading: true, error: "" });
  const [complex, setComplex] = useState(null);
  const [buildingNo, setBuildingNo] = useState("");
  const [unitNo, setUnitNo] = useState("");
  const [contact, setContact] = useState("");
  const [challenge, setChallenge] = useState(null);
  const [code, setCode] = useState("");
  const [method, setMethod] = useState("REGISTRATION_CODE");
  const [smsEnabled, setSmsEnabled] = useState(false);
  const [methodsError, setMethodsError] = useState(false);
  const [registrationCode, setRegistrationCode] = useState("");
  const [now, setNow] = useState(Date.now);
  const [lastIssued, setLastIssued] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [historyPage, setHistoryPage] = useState(0);
  const [historyRevision, setHistoryRevision] = useState(0);
  const [history, setHistory] = useState({ items: [], loading: true, error: "" });
  const pending = useRef(false);
  const mounted = useRef(false);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  useEffect(() => {
    const controller = new AbortController();
    getResidentVerificationMethods(controller.signal)
      .then(data => {
        if (!controller.signal.aborted) setSmsEnabled(data?.smsEnabled === true);
      })
      .catch(() => {
        if (!controller.signal.aborted) { setSmsEnabled(false); setMethodsError(true); }
      });
    return () => controller.abort();
  }, []);
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);
  useEffect(() => {
    const controller = new AbortController();
    setResult({ items: [], loading: true, error: "" });
    searchResidentComplexes(search.q, search.page, SIZE, controller.signal)
      .then(items => { if (!controller.signal.aborted) setResult({ items, loading: false, error: "" }); })
      .catch(e => { if (!controller.signal.aborted) setResult({ items: [], loading: false, error: explainError(e) }); });
    return () => controller.abort();
  }, [search]);
  useEffect(() => {
    const controller = new AbortController();
    setHistory({ items: [], loading: true, error: "" });
    myResidentEnrollments(historyPage, SIZE, controller.signal)
      .then(items => { if (!controller.signal.aborted) setHistory({ items, loading: false, error: "" }); })
      .catch(e => { if (!controller.signal.aborted) setHistory({ items: [], loading: false, error: explainError(e) }); });
    return () => controller.abort();
  }, [historyPage, historyRevision]);
  const remaining = challenge ? Math.max(0, Math.ceil((challenge.expiresAt - now) / 1000)) : 0;
  const cooldown = Math.max(0, Math.ceil((lastIssued + 60000 - now) / 1000));
  const valid = complex && buildingNo.trim() && unitNo.trim() && contact.trim();
  async function run(task) {
    if (pending.current) return;
    pending.current = true; setBusy(true); setError(""); setNotice("");
    try { await task(); }
    catch (e) { if (mounted.current) setError(explainError(e)); }
    finally { pending.current = false; if (mounted.current) setBusy(false); }
  }
  function reloadHistory() { setHistoryPage(0); setHistoryRevision(v => v + 1); }
  async function issue(e) {
    e?.preventDefault();
    if (!smsEnabled || !valid || cooldown || pending.current) return;
    await run(async () => {
      const started = Date.now();
      const data = await applyResident({ complexId: complex.id, buildingNo: buildingNo.trim(), unitNo: unitNo.trim(), contact: contact.trim() });
      if (!mounted.current) return;
      if (data?.id == null || data.status !== "PENDING_VERIFICATION") throw new Error("INVALID_RESPONSE");
      setChallenge({ id: data.id, demoCode: data.demoCode, expiresAt: started + 300000 });
      setLastIssued(Date.now()); setNow(Date.now()); setCode(""); reloadHistory();
    });
  }
  async function redeem(e) {
    e.preventDefault();
    if (!complex || !/^[0-9A-F]{32}$/.test(registrationCode) || pending.current) return;
    const generation = getGeneration();
    await run(async () => {
      const data = await redeemRegistrationCode(complex.id, registrationCode);
      if (data?.verified !== true) throw new Error("INVALID_RESPONSE");
      if (data.status === "APPROVED") {
        if (getGeneration() === generation) clearAuth("주민 등록이 승인되었습니다. 새 세대 접근 권한을 적용하려면 다시 로그인해 주세요.");
        return;
      }
      if (!mounted.current) return;
      if (data.status !== "PENDING_REVIEW") throw new Error("INVALID_RESPONSE");
      setRegistrationCode(""); setComplex(null);
      setNotice("등록 코드 인증을 완료했습니다. 단지 관리자의 주민 등록 심사를 기다려 주세요.");
      reloadHistory();
    });
  }
  async function verify(e) {
    e.preventDefault();
    if (!challenge || !remaining || !/^[0-9]{6}$/.test(code)) return;
    const generation = getGeneration();
    await run(async () => {
      let data;
      try { data = await verifyResident(challenge.id, code); }
      catch (e) {
        if (errorCode(e) === "INVALID_INPUT") { if (mounted.current) setError("인증번호가 올바르지 않거나 만료되었습니다."); return; }
        throw e;
      }
      if (data?.verified !== true) throw new Error("INVALID_RESPONSE");
      if (data.status === "APPROVED") {
        if (getGeneration() === generation) clearAuth("주민 등록이 승인되었습니다. 새 세대 접근 권한을 적용하려면 다시 로그인해 주세요.");
        return;
      }
      if (!mounted.current) return;
      if (data.status !== "PENDING_REVIEW") throw new Error("INVALID_RESPONSE");
      setChallenge(null); setCode(""); setContact(""); setBuildingNo(""); setUnitNo(""); setComplex(null);
      setNotice("연락처 인증을 완료했습니다. 단지 관리자의 주민 등록 심사를 기다려 주세요.");
      reloadHistory();
    });
  }
  return <>
    <PageHeader title="주민 등록·세대 연결" description={smsEnabled ? "관리사무소가 발급한 등록 코드 또는 연락처 인증으로 거주 세대를 연결합니다." : "관리사무소가 발급한 등록 코드로 거주 세대를 연결합니다."} />
    <div className="grid gap-5">
      {notice && <Alert tone="success">{notice}</Alert>}
      {error && <Alert tone="error">{error}</Alert>}
      {!challenge ? <>
        <Card className="grid gap-4 p-6">
          <h2 className="font-bold">거주 단지 검색</h2>
          <form className="flex items-end gap-3" onSubmit={e => { e.preventDefault(); setSearch({ q: query.trim(), page: 0 }); }}>
            <div className="flex-1"><Field label="단지명 또는 도로명 주소" maxLength={200} value={query} onChange={e => setQuery(e.target.value)} /></div>
            <Button type="submit" disabled={busy || result.loading}>검색</Button>
          </form>
          {result.loading && <p role="status">단지 조회 중…</p>}
          {result.error && <Alert tone="error">{result.error}</Alert>}
          {!result.loading && !result.error && !result.items.length && <p>주민 등록 가능한 단지가 없습니다. 관리사무소에 문의해 주세요.</p>}
          {result.items.map(item => <div key={item.id} className="flex items-center justify-between gap-3 border-t border-fixops-border py-3">
            <div><p className="font-semibold">{item.display_name}</p><p className="text-sm text-fixops-muted">{item.road_address}</p></div>
            <Button variant="secondary" disabled={busy} onClick={() => { setComplex(item); setRegistrationCode(""); setError(""); setNotice(""); }}>{complex?.id === item.id ? "선택됨" : "선택"}</Button>
          </div>)}
          <div className="flex justify-between"><Button variant="secondary" disabled={busy || result.loading || search.page === 0} onClick={() => setSearch(s => ({ ...s, page: s.page - 1 }))}>이전</Button><span>{search.page + 1} 페이지</span><Button variant="secondary" disabled={busy || result.loading || result.items.length < SIZE} onClick={() => setSearch(s => ({ ...s, page: s.page + 1 }))}>다음</Button></div>
        </Card>
        <Card className="grid gap-4 p-6">
          <h2 className="font-bold">주민 등록 인증</h2>
          <p>{complex ? complex.display_name + " · " + complex.road_address : "거주 단지를 먼저 선택해 주세요."}</p>
          {smsEnabled ? <Field label="인증 방식" as="select" value={method} disabled={busy} onChange={e => { setMethod(e.target.value); setRegistrationCode(""); setContact(""); setError(""); }}>
            <option value="REGISTRATION_CODE">관리사무소 등록 코드</option>
            <option value="SMS_OTP">연락처 SMS 인증</option>
          </Field> : <p className="text-sm font-semibold">관리사무소 등록 코드</p>}
          {methodsError && <Alert>인증 방식 정보를 조회하지 못했습니다. 등록 코드로 진행할 수 있습니다.</Alert>}
          {!smsEnabled || method === "REGISTRATION_CODE" ? <form className="grid gap-4" onSubmit={redeem}>
            <Alert>관리사무소에서 본인 확인 후 발급받은 일회용 등록 코드를 입력해 주세요.</Alert>
            <Field label="등록 코드 32자리" required maxLength={32} autoComplete="off" spellCheck={false} value={registrationCode} disabled={busy} onChange={e => setRegistrationCode(e.target.value.toUpperCase().replace(/[^0-9A-F]/g, "").slice(0,32))} />
            <Button type="submit" disabled={busy || !complex || registrationCode.length !== 32}>{busy ? "확인 중…" : "등록 코드 인증"}</Button>
          </form> : <form className="grid gap-4" onSubmit={issue}>
            <Field label="동" required maxLength={40} value={buildingNo} disabled={busy} onChange={e => setBuildingNo(e.target.value)} />
            <Field label="호수" required maxLength={40} value={unitNo} disabled={busy} onChange={e => setUnitNo(e.target.value)} />
            <Field label="본인 연락처" type="tel" autoComplete="tel" required maxLength={30} value={contact} disabled={busy} onChange={e => setContact(e.target.value)} />
            <Alert>관리사무소에 등록된 동·호수·연락처와 일치하는 정보를 입력해 주세요.</Alert>
            <Button type="submit" disabled={!valid || busy || cooldown > 0}>{busy ? "발급 중…" : cooldown ? cooldown + "초 후 발급 가능" : "인증번호 발송"}</Button>
          </form>}
        </Card>
      </> : <Card className="grid gap-4 p-6">
        <h2 className="font-bold">연락처 인증</h2>
        <p className="text-sm">{complex.display_name} · {buildingNo}동 {unitNo}호</p>
        <Alert>인증번호를 발급했습니다. 등록한 연락처를 확인해 주세요.</Alert>
        {challenge.demoCode && <Alert>데모 환경 인증번호: {challenge.demoCode}</Alert>}
        <form className="grid gap-4" onSubmit={verify}>
          <Field label="인증번호 6자리" inputMode="numeric" autoComplete="one-time-code" required maxLength={6} pattern="[0-9]{6}" value={code} disabled={busy} onChange={e => setCode(e.target.value.replace(/[^0-9]/g, "").slice(0, 6))} />
          <p className="text-sm">{remaining ? "남은 시간 " + Math.floor(remaining / 60) + ":" + String(remaining % 60).padStart(2, "0") : "인증번호가 만료되었습니다."}</p>
          <Button type="submit" disabled={busy || !remaining || code.length !== 6}>인증 및 주민 확인</Button>
        </form>
        <div className="flex gap-3"><Button variant="secondary" disabled={busy || cooldown > 0} onClick={issue}>{cooldown ? cooldown + "초 후 재발송" : "인증번호 재발송"}</Button><Button variant="secondary" disabled={busy} onClick={() => { setChallenge(null); setCode(""); setContact(""); setError(""); }}>정보 다시 입력</Button></div>
      </Card>}
      <Card className="grid gap-4 p-6">
        <div className="flex justify-between"><h2 className="font-bold">내 주민 등록 신청 내역</h2><Button variant="secondary" disabled={busy || history.loading} onClick={reloadHistory}>새로고침</Button></div>
        {history.loading && <p role="status">조회 중…</p>}
        {history.error && <Alert tone="error">{history.error}</Alert>}
        {!history.loading && !history.error && !history.items.length && <p>신청 내역이 없습니다.</p>}
        {history.items.map(item => <div key={item.id} className="flex flex-wrap justify-between gap-3 border-t border-fixops-border py-3"><div><p className="font-semibold">단지 #{item.complex_id} · {item.building_no}동 {item.unit_no}호</p><p className="text-xs text-fixops-muted">신청 #{item.id} · {item.verification_method === "REGISTRATION_CODE" ? "등록 코드" : "연락처 OTP"}</p></div><StatusBadge status={item.status} /></div>)}
        <div className="flex justify-between"><Button variant="secondary" disabled={history.loading || historyPage === 0} onClick={() => setHistoryPage(p => p - 1)}>이전</Button><span>{historyPage + 1} 페이지</span><Button variant="secondary" disabled={history.loading || history.items.length < SIZE} onClick={() => setHistoryPage(p => p + 1)}>다음</Button></div>
      </Card>
    </div>
  </>;
}
