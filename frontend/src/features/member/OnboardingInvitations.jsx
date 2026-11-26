import { useEffect, useState, useRef } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "./AuthProvider";
import * as api from "./memberApi";
import { explainError } from "../../api/errors";
import { Alert, Button, Card, Field } from "../../components/ui";

export function MyOnboardingInvitations() {
  const { clearAuth } = useAuth();
  const navigate=useNavigate();
  const [items,setItems]=useState([]),[error,setError]=useState(""),[busy,setBusy]=useState(false);
  const lock=useRef(false);
  const reload=()=>api.myOnboardingInvitations().then(setItems).catch(e=>setError(explainError(e)));
  useEffect(()=>{reload();},[]);
  if(!items.length&&!error) return null;
  return <Card className="grid gap-3 p-6">
    <h2 className="font-bold">조직 개설 초대</h2>
    <p className="text-sm text-fixops-muted">인증된 내 이메일로 발급된 초대만 표시됩니다.</p>
    {error && <Alert tone="error">{error}</Alert>}
    {!items.length && <p className="text-sm">대기 중인 초대가 없습니다.</p>}
    {items.map(x=><div key={x.id} className="grid gap-2 border-t border-fixops-border pt-3">
      <span>{x.organization_name} · {x.organization_kind}</span>
      <Button disabled={busy} onClick={async()=>{if(lock.current)return;lock.current=true;setBusy(true);setError("");try{
        const org=await api.acceptOnboardingInvitation(x.id);
        if(org.status==="ACTIVE"){clearAuth("조직 개설이 완료되었습니다. 다시 로그인해 주세요.");return;}
        navigate(`/complexes/register?organizationId=${encodeURIComponent(org.id)}`);
      }catch(e){setError(explainError(e));}finally{lock.current=false;setBusy(false);}}}>초대 수락 및 개설</Button>
    </div>)}
    <Button variant="secondary" disabled={busy} onClick={reload}>초대 새로고침</Button>
  </Card>;
}

export function PlatformOnboardingInvitations() {
  const [email,setEmail]=useState(""),[name,setName]=useState(""),[kind,setKind]=useState("BUILDING");
  const [page,setPage]=useState(0),[items,setItems]=useState([]),[error,setError]=useState(""),[notice,setNotice]=useState(""),[busy,setBusy]=useState(false);
  const lock=useRef(false);
  const reload=()=>api.listOnboardingInvitations(page).then(setItems).catch(e=>setError(explainError(e)));
  useEffect(()=>{reload();},[page]);
  async function run(action){if(lock.current)return;lock.current=true;setBusy(true);setError("");setNotice("");try{await action();await reload();}catch(e){setError(explainError(e));}finally{lock.current=false;setBusy(false);}}
  return <Card className="mb-6 grid gap-4 p-6">
    <h2 className="font-bold">조직 개설 초대 관리</h2>
    <form className="grid gap-3" onSubmit={e=>{e.preventDefault();run(async()=>{const r=await api.issueOnboardingInvitation({email:email.trim(),name:name.trim(),kind});setNotice(r.emailSent?"초대 및 이메일 발송 완료":"초대 등록 완료. 이메일은 발송되지 않았으므로 대상자에게 별도로 알려 주세요.");setEmail("");setName("");});}}>
      <Field label="초대 대상 이메일" type="email" required maxLength={255} value={email} onChange={e=>setEmail(e.target.value)}/>
      <Field label="사전 승인 조직명" required maxLength={200} value={name} onChange={e=>setName(e.target.value)}/>
      <Field label="산업 유형" as="select" value={kind} onChange={e=>setKind(e.target.value)}><option value="BUILDING">공동주택</option><option value="FACTORY">공장</option></Field>
      <Alert>공장 조직은 수락 시 활성화됩니다. 공동주택은 단지 관리권 심사를 통과해야 활성화됩니다.</Alert>
      <Button type="submit" disabled={busy}>72시간 조직 개설 초대 발급</Button>
    </form>
    {error && <Alert tone="error">{error}</Alert>}
    {notice && <Alert tone="success">{notice}</Alert>}
    {items.map(x=><div className="flex flex-wrap items-center justify-between gap-2 border-t border-fixops-border pt-3" key={x.id}>
      <span className="break-all">{x.organization_name} · {x.invitee_email} · {x.status}</span>
      {x.status==="PENDING" && <Button variant="secondary" disabled={busy} onClick={()=>run(()=>api.revokeOnboardingInvitation(x.id))}>초대 회수</Button>}
    </div>)}
    <div className="flex gap-2"><Button variant="secondary" disabled={busy||page===0} onClick={()=>setPage(p=>p-1)}>이전</Button><Button variant="secondary" disabled={busy||items.length<20} onClick={()=>setPage(p=>p+1)}>다음</Button></div>
  </Card>;
}
