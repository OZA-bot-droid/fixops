import { useEffect, useRef, useState } from "react";
import { useOrganization } from "./OrganizationProvider";
import { listRegistrationEntries, issueRegistrationCode, revokeRegistrationCode } from "./residentApi";
import { explainError } from "../../api/errors";
import { Alert, Button, Card, Modal } from "../../components/ui";
import { PageHeader } from "../../components/layout/AppLayout";
const SIZE = 20;
function CodeList({ complexId }) {
  const [page,setPage] = useState(0);
  const [revision,setRevision] = useState(0);
  const [result,setResult] = useState({items:[],loading:true,error:""});
  const [selected,setSelected] = useState(null);
  const [issued,setIssued] = useState(null);
  const [busy,setBusy] = useState(false);
  const [error,setError] = useState("");
  const [notice,setNotice] = useState("");
  const pending = useRef(false);
  const mounted = useRef(false);
  useEffect(() => { mounted.current=true; return () => { mounted.current=false; }; }, []);
  useEffect(() => {
    const controller = new AbortController();
    setResult({items:[],loading:true,error:""});
    listRegistrationEntries(complexId,page,SIZE,controller.signal).then(items => {
      if (!Array.isArray(items)) throw new Error("INVALID_RESPONSE");
      if (!controller.signal.aborted) setResult({items,loading:false,error:""});
    }).catch(e => { if (!controller.signal.aborted) setResult({items:[],loading:false,error:explainError(e)}); });
    return () => controller.abort();
  },[complexId,page,revision]);
  async function confirm() {
    if (!selected || pending.current) return;
    pending.current=true; setBusy(true); setError(""); setNotice("");
    try {
      if (selected.revoke) {
        await revokeRegistrationCode(complexId,selected.item.code_id);
        if (!mounted.current) return;
        setNotice("등록 코드를 회수했습니다.");
      } else {
        const data=await issueRegistrationCode(complexId,selected.item.id);
        if (!mounted.current) return;
        if (!/^[0-9A-F]{32}$/.test(data?.code ?? "")) throw new Error("INVALID_RESPONSE");
        setIssued({code:data.code,item:selected.item});
      }
      setSelected(null); setRevision(v=>v+1);
    } catch(e) { if(mounted.current) setError(explainError(e)); }
    finally { pending.current=false; if(mounted.current) setBusy(false); }
  }
  async function copy() {
    try { await navigator.clipboard.writeText(issued.code); if(mounted.current) setNotice("등록 코드를 복사했습니다."); }
    catch { if(mounted.current) setNotice("복사할 수 없습니다. 화면의 코드를 직접 선택해 복사해 주세요."); }
  }
  function open(item,revoke=false) { setError(""); setNotice(""); setSelected({item,revoke}); }
  return <Card className="grid gap-4 p-6">
    <div className="flex flex-wrap items-center justify-between gap-3"><h2 className="font-bold">활성 입주자 명부</h2><Button variant="secondary" disabled={busy||result.loading} onClick={()=>setRevision(v=>v+1)}>새로고침</Button></div>
    <Alert>본인 확인을 마친 입주자에게만 코드를 발급하세요. 코드는 24시간 동안 1회 사용할 수 있으며 재발급 시 기존 미사용 코드는 무효화됩니다.</Alert>
    {notice && <Alert>{notice}</Alert>}
    {result.error && <Alert tone="error">{result.error}</Alert>}
    {result.loading && <p role="status">조회 중…</p>}
    {!result.loading&&!result.error&&!result.items.length&&<p>활성 입주자 명부가 없습니다.</p>}
    {result.items.map(item=><div key={item.id} className="flex flex-wrap items-center justify-between gap-3 border-t border-fixops-border py-4">
      <div><p className="font-semibold">{item.building_no}동 {item.unit_no}호 · {item.name}</p><p className="text-sm text-fixops-muted">{item.linked?"연결 완료":item.pending_review?"심사 대기":item.code_id?"코드 발급됨":"미연결"}</p>{item.expires_at&&<p className="text-xs text-fixops-muted">만료: {new Date(item.expires_at).toLocaleString("ko-KR")}</p>}</div>
      <div className="flex flex-wrap gap-2"><Button disabled={busy||item.linked||item.pending_review||!!issued} onClick={()=>open(item)}>{item.code_id?"재발급":"발급"}</Button>{item.code_id&&<Button variant="danger" disabled={busy||!!issued} onClick={()=>open(item,true)}>회수</Button>}</div>
    </div>)}
    <div className="flex justify-between"><Button variant="secondary" disabled={busy||result.loading||page===0} onClick={()=>setPage(p=>p-1)}>이전</Button><span>{page+1} 페이지</span><Button variant="secondary" disabled={busy||result.loading||result.items.length<SIZE} onClick={()=>setPage(p=>p+1)}>다음</Button></div>
    <Modal open={!!selected} title={selected?.revoke?"등록 코드 회수":"등록 코드 발급"} onClose={()=>{if(!pending.current)setSelected(null);}} footer={<><Button variant="secondary" disabled={busy} onClick={()=>setSelected(null)}>취소</Button><Button disabled={busy} onClick={confirm}>{busy?"처리 중…":"확인"}</Button></>}>
      {selected&&<p>{selected.item.building_no}동 {selected.item.unit_no}호 · {selected.item.name}</p>}
      <Alert>{selected?.revoke?"미사용 코드를 무효화합니다.":"입주자 본인 확인을 완료했습니까? 기존 미사용 코드는 회수됩니다."}</Alert>
      {error&&<Alert tone="error">{error}</Alert>}
    </Modal>
    <Modal open={!!issued} title="일회용 등록 코드" onClose={()=>setIssued(null)} footer={<><Button variant="secondary" onClick={copy}>복사</Button><Button onClick={()=>{setIssued(null);setNotice("");}}>확인 및 닫기</Button></>}>
      {issued&&<><p>{issued.item.building_no}동 {issued.item.unit_no}호 · {issued.item.name}</p><p className="my-4 break-all select-all font-mono font-bold">{issued.code}</p><Alert>원문은 다시 조회할 수 없습니다. 확인된 입주자에게만 안전하게 전달하세요.</Alert>{notice&&<p role="status">{notice}</p>}</>}
    </Modal>
  </Card>;
}
export default function ResidentRegistrationCodePage(){
  const {activeOrganizationKind,activeOrganizationId,activeOrganizationName,activeComplexId,complexes}=useOrganization();
  const complex=complexes.find(c=>String(c.id)===String(activeComplexId));
  return <><PageHeader title="입주자 등록 코드 관리" description={activeOrganizationName||"업무 조직"}/>{activeOrganizationKind!=="BUILDING"||!complex||complex.access_scope!=="ADMIN"?<Alert>관리 권한이 있는 공동주택 단지를 선택해 주세요.</Alert>:<><p className="mb-4 font-semibold">{complex.display_name}</p><CodeList key={activeOrganizationId+":"+activeComplexId} complexId={activeComplexId}/></>}</>;
}
