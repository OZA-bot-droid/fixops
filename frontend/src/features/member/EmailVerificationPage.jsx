import { onboardingIntent, onboardingDestination } from "./onboardingPolicy";
import { useEffect, useRef, useState } from "react";
import { Link, Navigate, useLocation } from "react-router-dom";
import { useAuth } from "./AuthProvider";
import { requestEmailVerification, verifyEmail } from "./memberApi";
import { explainError, errorCode } from "../../api/errors";
import { Alert, Button, Card, Field } from "../../components/ui";
import { PageHeader } from "../../components/layout/AppLayout";

export default function EmailVerificationPage() {
  const { user, refreshAuth } = useAuth();
  const intent = onboardingIntent(useLocation().search);
  const [challenge, setChallenge] = useState(null);
  const [code, setCode] = useState("");
  const [now, setNow] = useState(Date.now);
  const [busy, setBusy] = useState(false);
  const [verified, setVerified] = useState(false);
  const [error, setError] = useState("");
  const inFlight = useRef(false);
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);
  const remaining = challenge ? Math.max(0, Math.ceil((challenge.expiresAt - now) / 1000)) : 0;
  const resend = challenge ? Math.max(0, Math.ceil((challenge.resendAt - now) / 1000)) : 0;
  async function run(task) {
    if (inFlight.current) return;
    inFlight.current = true;
    setBusy(true);
    setError("");
    try { await task(); }
    catch (e) { setError(explainError(e)); }
    finally { inFlight.current = false; setBusy(false); }
  }
  async function send() {
    if (resend || verified) return;
    await run(async () => {
      const started = Date.now();
      const data = await requestEmailVerification();
      if (!data?.challengeId) throw new Error("INVALID_VERIFICATION_RESPONSE");
      setChallenge({ id: data.challengeId, demoCode: data.demoCode,
        expiresAt: started + 300000, resendAt: Date.now() + 60000 });
      setNow(Date.now());
      setCode("");
    });
  }
  async function confirm(e) {
    e.preventDefault();
    if (!challenge || !remaining || !/^[0-9]{6}$/.test(code)) return;
    await run(async () => {
      let data;
      try { data = await verifyEmail(challenge.id, code); }
      catch (e) {
        if (errorCode(e) === "INVALID_INPUT") {
          setError("인증번호가 올바르지 않거나 만료되었습니다. 필요하면 다시 발급해 주세요.");
          return;
        }
        throw e;
      }
      if (data?.emailVerified !== true) throw new Error("INVALID_VERIFICATION_RESPONSE");
      setVerified(true);
      setChallenge(null);
      setCode("");
      await refreshAuth();
    });
  }
  if (user?.emailVerified === true) return <Navigate to={onboardingDestination(user, intent)} replace />;
  return (
    <>
      <PageHeader title="이메일 인증" description="조직 신청 및 주민 등록 전 계정 이메일을 인증해 주세요." />
      <Card className="mx-auto grid max-w-lg gap-5 p-7">
        <Field label="이메일 주소" value={user?.email || ""} readOnly disabled />
        {verified ? <>
          <Alert tone="success">이메일 인증을 완료했습니다. 회원 정보를 다시 확인해 주세요.</Alert>
          <Button disabled={busy} onClick={() => run(refreshAuth)}>회원 정보 갱신</Button>
        </> : <>
          <Button disabled={busy || resend > 0} onClick={send}>
            {busy ? "처리 중…" : resend ? `${resend}초 후 재발송` : challenge ? "인증번호 재발송" : "인증번호 발송"}
          </Button>
          {challenge && <form className="grid gap-4" onSubmit={confirm}>
            <Alert>인증번호를 발급했습니다. 이메일을 확인해 주세요.</Alert>
            {challenge.demoCode && <Alert>데모 환경 인증번호: {challenge.demoCode}</Alert>}
            <Field label="인증번호 6자리" inputMode="numeric" autoComplete="one-time-code"
              maxLength={6} value={code} required pattern="[0-9]{6}"
              onChange={e => setCode(e.target.value.replace(/[^0-9]/g, "").slice(0, 6))} />
            <p className="text-sm text-fixops-muted">
              {remaining ? `남은 시간 ${Math.floor(remaining / 60)}:${String(remaining % 60).padStart(2, "0")}` : "인증번호가 만료되었습니다."}
            </p>
            <Button type="submit" disabled={busy || !remaining || code.length !== 6}>인증 완료</Button>
          </form>}
        </>}
        {error && <Alert tone="error">{error}</Alert>}
        <Link to="/profile" className="text-sm text-fixops-primary">프로필로 돌아가기</Link>
      </Card>
    </>
  );
}
