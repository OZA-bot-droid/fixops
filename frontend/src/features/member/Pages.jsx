import { canApplyOrganization, onboardingIntent, onboardingQuery, onboardingDestination } from "./onboardingPolicy";
import { PlatformOnboardingInvitations } from "./OnboardingInvitations";
import { useEffect, useState } from "react";
import { Link, Navigate, useNavigate, useLocation } from "react-router-dom";
import { useAuth } from "./AuthProvider";
import * as api from "./memberApi";
import MyOrganizationsCard from "./MyOrganizationsCard";
import { explainError as explain } from "../../api/errors";
import {
  Alert,
  Button,
  Card,
  EmptyState,
  Field,
  Modal,
  StatusBadge,
} from "../../components/ui";
import { PageHeader } from "../../components/layout/AppLayout";
const titleCell = "px-5 py-4 text-left text-xs font-semibold text-fixops-muted";
const bodyCell = "px-5 py-4 text-[13px]";
function AuthShell({ title, description, children }) {
  return (
    <div className="grid min-h-screen bg-fixops-background lg:grid-cols-[minmax(400px,46%)_1fr]">
      <section className="flex min-h-[360px] flex-col bg-fixops-sidebar px-8 py-10 text-white sm:px-16 lg:min-h-screen lg:py-[75px]">
        <Link to="/" className="text-[37px] font-bold">
          ✦ FixOps
        </Link>
        <div className="my-auto py-12">
          <h1 className="text-[27px] font-bold leading-relaxed">
            시설 유지보수의 모든 순간을 연결합니다.
          </h1>
          <p className="mt-5 max-w-xl text-lg leading-7 text-[#b8c9e4]">
            고장 접수부터 AI 점검, 예약과 자산 관리까지
            <br />한 곳에서 관리하는 스마트 운영 플랫폼
          </p>
          <div className="mt-16 flex flex-wrap gap-5">
            {["AI 점검", "실시간 작업", "조직별 보안"].map((x) => (
              <div
                key={x}
                className="flex h-[94px] w-[128px] items-end rounded-[14px] bg-[#233858] p-4 text-sm font-bold"
              >
                {x}
              </div>
            ))}
          </div>
        </div>
      </section>
      <section className="flex items-center justify-center px-5 py-12">
        <Card className="w-full max-w-[574px] rounded-[24px] px-7 py-10 sm:px-[55px] sm:py-12">
          <h2 className="text-[29px] font-bold">{title}</h2>
          <p className="mt-2 text-[14px] text-fixops-muted">
            {description || "FixOps 계정으로 안전하게 시작하세요."}
          </p>
          <div className="mt-12">{children}</div>
        </Card>
      </section>
    </div>
  );
}
export function LoginPage() {
  const { signIn, loggedIn, loading, signingIn, user } = useAuth();
  const intent = onboardingIntent(useLocation().search);
  const [email, setEmail] = useState(""),
    [password, setPassword] = useState(""),
    [error, setError] = useState(""),
    [submitting, setSubmitting] = useState(false);
  if (loading) {
    return (
      <div
        className="
      grid min-h-screen
      place-items-center
      text-fixops-muted
    "
      >
        인증 상태 확인 중…
      </div>
    );
  }

  if (loggedIn) {
    return <Navigate to={onboardingDestination(user, intent)} replace />;
  }
  return (
    <AuthShell title="로그인">
      <form
        className="grid gap-7"
        onSubmit={async (e) => {
          e.preventDefault();

          if (submitting || signingIn || loading) {
            return;
          }

          setSubmitting(true);
          setError("");

          try {
            await signIn(email, password);
          } catch (e) {
            setError(explain(e));
          } finally {
            setSubmitting(false);
          }
        }}
      >
        <Field
          label="이메일 주소"
          type="email"
          autoComplete="email"
          placeholder="name@example.com"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
        <Field
          label="비밀번호"
          type="password"
          autoComplete="current-password"
          placeholder="입력하세요"
          required
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
        {error && <Alert tone="error">{error}</Alert>}
        <div className="mt-11 grid gap-5">
          <Button
            type="submit"
            disabled={submitting || signingIn || loading}
            className="w-full"
          >
            {submitting || signingIn ? "로그인 중…" : "로그인"}
          </Button>
          <p className="text-center text-[13px]">
            계정이 없으신가요?{" "}
            <Link to={"/signup" + onboardingQuery(intent)} className="font-semibold text-fixops-primary">
              회원가입
            </Link>
          </p>
          <Link
            to="/forgot-password"
            className="text-center text-xs text-fixops-muted"
          >
            비밀번호를 잊으셨나요?
          </Link>
        </div>
      </form>
    </AuthShell>
  );
}
export function SignupPage() {
  const initialIntent = onboardingIntent(useLocation().search);
  const [intent, setIntent] = useState(initialIntent);
  const [email, setEmail] = useState(""),
    [name, setName] = useState(""),
    [password, setPassword] = useState(""),
    [msg, setMsg] = useState(""),
    [error, setError] = useState(""),
    [pending, setPending] = useState(false);
  return (
    <AuthShell title="회원가입">
      <form
        className="grid gap-5"
        onSubmit={async (e) => {
          e.preventDefault();
          setError("");
          setMsg("");
          setPending(true);
          try {
            const v = await api.signup({ email, name, password });
            setMsg(
              v.emailVerified
                ? "계정이 생성되었습니다. 로그인할 수 있습니다."
                : "계정이 생성되었습니다. 로그인 후 이메일 인증을 완료해 주세요.",
            );
          } catch (err) {
            setError(explain(err));
          } finally {
            setPending(false);
          }
        }}
      >
        <Field label="서비스 이용 목적" as="select" value={intent} onChange={e=>setIntent(e.target.value)}>
          <option value="">나중에 선택</option>
          <option value="resident">입주민으로 이용</option>
          <option value="member">기술자·조직 구성원으로 이용</option>
          <option value="organization">조직에 FixOps 도입</option>
          <option value="invitation">플랫폼 조직 개설 초대를 받음</option>
        </Field>
        <p className="text-xs text-fixops-muted">이용 목적 선택은 화면 이동에만 사용되며 권한을 부여하지 않습니다.</p>
        <Field
          label="이메일 주소"
          type="email"
          required
          autoComplete="email"
          placeholder="name@example.com"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
        <Field
          label="이름"
          required
          maxLength={100}
          placeholder="성함을 입력하세요"
          autoComplete="name"
          value={name}
          onChange={(e) => setName(e.target.value)}
        />
        <Field
          label="비밀번호"
          required
          type="password"
          minLength={12}
          autoComplete="new-password"
          placeholder="12자 이상 입력하세요"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
        <p className="text-xs text-fixops-muted">
          계정 생성 후 로그인하여 이메일 인증을 완료해 주세요.
        </p>
        {error && <Alert tone="error">{error}</Alert>}
        {msg && <Alert tone="success">{msg}</Alert>}
        <Button type="submit" className="mt-4 w-full" disabled={pending}>
          {pending ? "처리 중…" : "계정 만들기"}
        </Button>
        <p className="text-center text-xs">
          이미 계정이 있으신가요?{" "}
          <Link to={"/login" + onboardingQuery(intent)} className="font-semibold text-fixops-primary">
            로그인
          </Link>
        </p>
      </form>
    </AuthShell>
  );
}
export function PasswordResetPage() {
  return (
    <AuthShell
      title="비밀번호 재설정"
      description="계정 보안을 위해 인증 후 비밀번호를 변경할 수 있습니다."
    >
      <Alert>
        비밀번호 재설정 메일 발송 API가 아직 구현되지 않았습니다. 기능이 준비될
        때까지 기존 비밀번호로 로그인해 주세요.
      </Alert>
      <Link
        to="/login"
        className="mt-8 inline-block text-sm font-semibold text-fixops-primary"
      >
        로그인으로 돌아가기
      </Link>
    </AuthShell>
  );
}
export function MemberHome() {
  const { user } = useAuth();
  return (
    <>
      <PageHeader
        title="대시보드"
        description="조직 운영과 내 계정 상태를 한눈에 확인하세요."
      />
      {user?.emailVerified !== true && !user?.platformAdmin && (
        <div className="mb-5"><Alert>이메일 인증이 필요합니다. <Link to="/email-verification" className="font-semibold underline">이메일 인증하기</Link></Alert></div>
      )}
      <div className="grid gap-5 lg:grid-cols-3">
        {[
          { name: "내 조직 수", value: user?.memberships?.length || 0 },
          {
            name: "플랫폼 권한",
            value: user?.platformAdmin ? "PLATFORM_ADMIN" : "일반 계정",
          },
          { name: "계정 이메일", value: user?.email || "—" },
        ].map((x) => (
          <Card key={x.name} className="p-7">
            <p className="text-sm text-fixops-muted">{x.name}</p>
            <p className="mt-5 break-all text-xl font-bold">{x.value}</p>
          </Card>
        ))}
      </div>
      <div className="mt-6 grid gap-5 lg:grid-cols-2">
        {canApplyOrganization(user) && <Card className="p-7">
          <h2 className="text-lg font-semibold">서비스 도입</h2>
          <p className="mt-3 text-sm text-fixops-muted">
            조직 개설 신청과 내 신청 상태를 관리할 수 있습니다.
          </p>
          <Link
            to="/organizations/apply"
            className="mt-5 inline-block text-sm font-semibold text-fixops-primary"
          >
            서비스 도입 신청 →
          </Link>
        </Card>}
        <Card className="p-7">
          <h2 className="text-lg font-semibold">계정 설정</h2>
          <p className="mt-3 text-sm text-fixops-muted">
            내 프로필과 연결된 조직 정보를 확인합니다.
          </p>
          <Link
            to="/profile"
            className="mt-5 inline-block text-sm font-semibold text-fixops-primary"
          >
            프로필 보기 →
          </Link>
        </Card>
      </div>
    </>
  );
}
export function OrganizationApply() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [name, setName] = useState(""),
    [kind, setKind] = useState("BUILDING"),
    [msg, setMsg] = useState(""),
    [error, setError] = useState(""),
    [items, setItems] = useState([]),
    [pending, setPending] = useState(false);
  const reload = () =>
    api
      .myOrgs()
      .then((v) => setItems(Array.isArray(v) ? v : []))
      .catch((e) => setError(explain(e)));
  useEffect(() => {
    reload();
  }, []);
  return (
    <>
      <PageHeader
        title="서비스 도입 신청"
        description="조직 규정과 작업 흐름에 따라 정확히 등록해 주세요."
      />
      <div className="grid items-start gap-7 xl:grid-cols-[minmax(0,735px)_minmax(270px,335px)]">
        {canApplyOrganization(user) ? <Card className="p-7">
          <h2 className="text-[19px] font-bold">기본 정보</h2>
          <form
            className="mt-8 grid gap-7"
            onSubmit={async (e) => {
              e.preventDefault();
              setError("");
              setMsg("");
              setPending(true);
              try {
                const created = await api.applyOrg({ name: name.trim(), kind });
                if (kind === "BUILDING" && created?.id != null) {
                  navigate(`/complexes/register?organizationId=${encodeURIComponent(created.id)}`);
                  return;
                }
                setMsg(
                  "조직 개설 신청이 접수되었습니다. 플랫폼 관리자 승인 후 이용할 수 있습니다.",
                );
                setName("");
                reload();
              } catch (err) {
                setError(explain(err));
              } finally {
                setPending(false);
              }
            }}
          >
            <Field
              label="조직명"
              required
              maxLength={200}
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="조직명을 입력하세요"
            />
            <Field
              label="산업 유형"
              as="select"
              value={kind}
              onChange={(e) => setKind(e.target.value)}
            >
              <option value="BUILDING">BUILDING · 공동주택</option>
              <option value="FACTORY">FACTORY · 공장</option>
            </Field>
            <Alert>
              조직 개설 신청과 조직 관리 권한 승인은 별도 절차입니다. 공동주택
              관리권 확인은 담당자 검토가 필요합니다.
            </Alert>
            {error && <Alert tone="error">{error}</Alert>}
            {msg && <Alert tone="success">{msg}</Alert>}
            <div className="flex justify-end pt-4">
              <Button type="submit" disabled={pending}>
                {pending ? "신청 중…" : "저장 및 계속"}
              </Button>
            </div>
          </form>
        </Card> : <Card className="p-6"><Alert>현재 계정은 일반 조직 개설을 신청할 수 없습니다. 플랫폼 관리자의 초대를 이용해 주세요.</Alert></Card>}
        <Card className="p-6">
          <h2 className="text-lg font-bold">입력 가이드</h2>
          <p className="mt-4 text-sm leading-6 text-fixops-muted">
            조직별 권한과 상태를 확인합니다. 사업자등록번호 공공 API 검증은
            사용하지 않습니다.
          </p>
          <div className="mt-6 inline-flex rounded-full bg-blue-50 px-3 py-2 text-xs font-semibold text-fixops-primary">
            안전한 데이터 관리
          </div>
        </Card>
      </div>
      <Card className="mt-7 p-6">
        <h2 className="text-lg font-bold">내 신청 내역</h2>
        <div className="mt-4 overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b border-fixops-border">
                <th className={titleCell}>조직명</th>
                <th className={titleCell}>유형</th>
                <th className={titleCell}>상태</th>
              </tr>
            </thead>
            <tbody>
              {items.map((x) => (
                <tr key={x.id} className="border-b border-fixops-border">
                  <td className={bodyCell}>{x.name}</td>
                  <td className={bodyCell}>{x.kind}</td>
                  <td className={bodyCell}>
                    <StatusBadge status={x.status} />
                    {x.kind === "BUILDING" && x.status === "PENDING_APPROVAL" && <Link className="ml-3 text-fixops-primary underline" to={`/complexes/register?organizationId=${encodeURIComponent(x.id)}`}>단지 등록</Link>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {items.length === 0 && (
            <EmptyState title="조직 신청 내역이 없습니다." />
          )}
        </div>
      </Card>
    </>
  );
}
export function PlatformOrganizations() {
  const [items, setItems] = useState([]),
    [error, setError] = useState(""),
    [selected, setSelected] = useState(null),
    [busy, setBusy] = useState(false),
    [reason, setReason] = useState("");
  const reload = () =>
    api
      .pendingOrgs()
      .then((v) => setItems(Array.isArray(v) ? v : []))
      .catch((e) => setError(explain(e)));
  useEffect(() => {
    reload();
  }, []);
  async function confirm() {
    if (!selected) return;
    if (!selected.approve && !reason.trim()) {
      setError("반려 사유를 입력해 주세요.");
      return;
    }
    setBusy(true);
    setError("");
    try {
      await api.decideOrg(selected.id, selected.approve, reason);
      setSelected(null);
      setReason("");
      reload();
    } catch (e) {
      setError(explain(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <PageHeader
        title="조직 개설 관리"
        description="플랫폼 관리자가 접수된 조직의 개설 신청을 검토하고 결정합니다."
        action={<Link to="/platform/complex-managements" className="font-semibold text-fixops-primary underline">단지 관리권 심사</Link>}
      />
      {error && (
        <div className="mb-5">
          <Alert tone="error">{error}</Alert>
        </div>
      )}
      <PlatformOnboardingInvitations />
      <Card className="overflow-hidden">
        <div className="flex items-center justify-between border-b border-fixops-border p-6">
          <h2 className="font-semibold">승인 대기 조직</h2>
          <Button variant="secondary" onClick={reload}>
            새로고침
          </Button>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead className="bg-fixops-background">
              <tr>
                <th className={titleCell}>조직명</th>
                <th className={titleCell}>산업 유형</th>
                <th className={titleCell}>신청자 ID</th>
                <th className={titleCell}>상태</th>
                <th className={titleCell}>검토</th>
              </tr>
            </thead>
            <tbody>
              {items.map((x) => (
                <tr key={x.id} className="border-t border-fixops-border">
                  <td className={bodyCell}>{x.name}</td>
                  <td className={bodyCell}>{x.kind}</td>
                  <td className={bodyCell}>{x.applicantId}</td>
                  <td className={bodyCell}>
                    <StatusBadge status={x.status || "PENDING"} />
                  </td>
                  <td className={bodyCell}>
                    <div className="flex gap-2">
                      <Button
                        onClick={() =>
                          setSelected({ id: x.id, name: x.name, kind: x.kind, approve: true })
                        }
                      >
                        승인
                      </Button>
                      <Button
                        variant="secondary"
                        onClick={() =>
                          setSelected({
                            id: x.id,
                            name: x.name,
                            approve: false,
                          })
                        }
                      >
                        반려
                      </Button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {items.length === 0 && (
            <EmptyState title="대기 중인 조직 신청이 없습니다." />
          )}
        </div>
      </Card>
      <Modal
        open={!!selected}
        title={selected?.approve ? "조직 개설 승인" : "조직 개설 반려"}
        description={`${selected?.name || ""} 조직에 대한 결정을 확인하세요.`}
        onClose={() => { if (!busy) setSelected(null); }}
        footer={
          <>
            <Button variant="secondary" disabled={busy} onClick={() => setSelected(null)}>
              취소
            </Button>
            <Button
              disabled={busy}
              variant={selected?.approve ? "primary" : "danger"}
              onClick={confirm}
            >
              {busy
                ? "처리 중…"
                : selected?.approve
                  ? "승인 확인"
                  : "반려 확인"}
            </Button>
          </>
        }
      >
        {selected?.approve ? (
          <Alert>
            조직 승인 시 신청자에게 첫 ADMIN 역할이 부여됩니다.
            {selected.kind === "BUILDING" && <> 공동주택 조직은 단지 관리권 심사를 먼저 완료해야 합니다. <Link to="/platform/complex-managements" className="underline">단지 심사로 이동</Link></>}
          </Alert>
        ) : (
          <Field
            label="반려 사유"
            as="textarea"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            required
            placeholder="반려 사유를 입력하세요"
          />
        )}
      </Modal>
    </>
  );
}
export function ProfilePage() {
  const { user, signOut, signOutAll, signingOut } = useAuth();
  const [confirmLogoutAll, setConfirmLogoutAll] = useState(false);
  return (
    <>
      <PageHeader
        title="내 프로필·계정 설정"
        description="
          계정 및 조직 소속 정보를 확인하세요.
        "
      />

      <div
        className="
        grid items-start gap-7
        xl:grid-cols-[
          minmax(0,735px)
          minmax(270px,335px)
        ]
      "
      >
        <div className="grid gap-7">
          <Card className="p-7">
            <h2
              className="
              text-lg font-bold
            "
            >
              기본 정보
            </h2>

            <div
              className="
              mt-8 grid gap-6
            "
            >
              <Field label="이름" value={user?.name || ""} disabled readOnly />

              <Field
                label="이메일"
                value={user?.email || ""}
                disabled
                readOnly
              />

              <Field label="이메일 인증" value={user?.emailVerified === true ? "인증 완료" : "미인증"} disabled readOnly />
              {user?.emailVerified !== true && <Link to="/email-verification" className="text-sm font-semibold text-fixops-primary">이메일 인증하기 →</Link>}
              <Field
                label="플랫폼 관리자"
                value={user?.platformAdmin ? "예" : "아니오"}
                disabled
                readOnly
              />

              <Field
                label="소속 조직 수"
                value={String(user?.memberships?.length || 0)}
                disabled
                readOnly
              />
            </div>
          </Card>

          <MyOrganizationsCard />
          <Card className="p-7">
            <h2
              className="
              text-lg font-bold
            "
            >
              계정 보안
            </h2>

            <div
              className="
              mt-6 grid gap-6
            "
            >
              <div
                className="
                flex flex-wrap
                items-center
                justify-between gap-4
              "
              >
                <div>
                  <p
                    className="
                    text-sm font-semibold
                  "
                  >
                    현재 기기 로그아웃
                  </p>

                  <p
                    className="
                    mt-2 text-xs
                    text-fixops-muted
                  "
                  >
                    현재 로그인 세션만 종료합니다.
                  </p>
                </div>

                <Button
                  variant="secondary"
                  disabled={signingOut}
                  onClick={() => {
                    signOut().catch(console.error);
                  }}
                >
                  로그아웃
                </Button>
              </div>

              <div
                className="
                border-t
                border-fixops-border
                pt-6
              "
              >
                <div
                  className="
                  flex flex-wrap
                  items-center
                  justify-between gap-4
                "
                >
                  <div>
                    <p
                      className="
                      text-sm font-semibold
                    "
                    >
                      모든 기기 로그아웃
                    </p>

                    <p
                      className="
                      mt-2 text-xs
                      text-fixops-muted
                    "
                    >
                      현재 계정의 모든 로그인 세션을 종료합니다.
                    </p>
                  </div>

                  <Button
                    variant="danger"
                    disabled={signingOut}
                    onClick={() => setConfirmLogoutAll(true)}
                  >
                    전체 로그아웃
                  </Button>
                </div>
              </div>
            </div>
          </Card>
        </div>

        <Card className="p-6">
          <h2 className="font-bold">계정 보안 안내</h2>

          <p
            className="
            mt-4 text-sm leading-6
            text-fixops-muted
          "
          >
            공용 PC 또는 사용하지 않는 기기의 로그인 세션을 종료하려면 모든 기기
            로그아웃 기능을 이용하세요.
          </p>
        </Card>
      </div>

      <Modal
        open={confirmLogoutAll}
        title="모든 기기 로그아웃"
        description="
          현재 계정으로 로그인된 모든 기기의
          세션을 종료합니다.
        "
        onClose={() => setConfirmLogoutAll(false)}
        footer={
          <>
            <Button
              variant="secondary"
              disabled={signingOut}
              onClick={() => setConfirmLogoutAll(false)}
            >
              취소
            </Button>

            <Button
              variant="danger"
              disabled={signingOut}
              onClick={async () => {
                try {
                  await signOutAll();
                } finally {
                  setConfirmLogoutAll(false);
                }
              }}
            >
              {signingOut ? "처리 중…" : "전체 로그아웃"}
            </Button>
          </>
        }
      >
        <Alert>
          현재 브라우저를 포함하여 로그인되어 있는 모든 기기에서 다시 로그인해야
          합니다.
        </Alert>
      </Modal>
    </>
  );
}
export function UnavailablePage({ title, description }) {
  return (
    <>
      <PageHeader
        title={title}
        description={
          description ||
          "해당 기능은 공통 디자인에 따라 준비되어 있으며 서버 API 연결 후 활성화됩니다."
        }
      />
      <Card className="p-8">
        <EmptyState
          title="서버 API 연동 대기"
          message="임의의 데이터나 저장 성공 메시지를 표시하지 않습니다."
        />
      </Card>
    </>
  );
}
