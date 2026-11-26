import { useMemo, useState, useRef } from "react";
import { Alert, Button, Card, Field, Modal } from "../../components/ui";
import { explainError } from "../../api/errors";
import { previewBuildingUnits, commitBuildingUnits, getBuildingRegisterHint } from "./locationApi";

const order=(a,b)=>String(a).localeCompare(String(b),"ko",{numeric:true})||String(a).localeCompare(String(b),"ko");
const empty={sigunguCd:"",bjdongCd:"",platGbCd:"0",bun:"",ji:"0"};
export default function BuildingUnitImport({organizationId,complexId,complex,roots,onImported,locationsLoading,locationsError}){
  const [parcel,setParcel]=useState(empty);
  const [root,setRoot]=useState("");
  const [preview,setPreview]=useState(null);
  const [selected,setSelected]=useState([]);
  const [confirmed,setConfirmed]=useState(false);
  const [operation,setOperation]=useState("");
  const busy=!!operation;
  const inFlight=useRef(false);
  const [result,setResult]=useState(null);
  const [buildingFilter,setBuildingFilter]=useState("");
  const effectiveRoot=roots.length===1?String(roots[0].id):roots.some(x=>String(x.id)===root)?root:"";
  const [confirmedRoot,setConfirmedRoot]=useState("");
  const groups=useMemo(()=>{
    const byBuilding=new Map();
    for(const unit of preview?.units??[]){
      if(!byBuilding.has(unit.buildingNo))byBuilding.set(unit.buildingNo,[]);
      byBuilding.get(unit.buildingNo).push(unit);
    }
    return [...byBuilding].sort(([a],[b])=>order(a,b)).map(([code,units])=>({code,units:units.sort((a,b)=>order(a.unitNo,b.unitNo)||order(a.recordId,b.recordId))}));
  },[preview]);
  const visibleUnits=useMemo(()=>groups.filter(g=>!buildingFilter||g.code===buildingFilter).flatMap(g=>g.units),[groups,buildingFilter]);
  const selectedSet=useMemo(()=>new Set(selected),[selected]);
  const [error,setError]=useState("");
  const [notice,setNotice]=useState("");
  const [page,setPage]=useState(0);
  const [excludedPage,setExcludedPage]=useState(0);
  const [lookedUp,setLookedUp]=useState(null);
  const blockers=[
    ...(locationsLoading?["위치 목록 조회 중"]:locationsError?["위치 목록 조회 실패 — 새로고침 필요"]:!roots.length?["단지 루트 등록 필요"]:!effectiveRoot?["등록할 단지 루트 선택 필요"]:[]),
    ...(!selected.length?["등록할 세대 선택 필요"]:[]),
    ...(!confirmed||confirmedRoot!==effectiveRoot?["실제 주거 세대 확인 필요"]:[]),
    ...(!lookedUp?["전유부 조회 필요"]:[])
  ];
  function start(name){if(inFlight.current)return false;inFlight.current=true;setOperation(name);return true;}
  function finish(){inFlight.current=false;setOperation("");}
  function toggleGroup(group,checked){
    const ids=new Set(group.units.map(x=>x.recordId));
    setSelected(old=>checked?[...new Set([...old,...ids])]:old.filter(id=>!ids.has(id)));
    setConfirmed(false);
  }
  function change(key,value){setParcel(p=>({...p,[key]:value}));setPreview(null);setSelected([]);setConfirmed(false);setLookedUp(null);setNotice("");setError("");}
  async function autoFill(){
    if(!start("hint"))return;
    setError("");setNotice("");setPreview(null);setSelected([]);setConfirmed(false);setLookedUp(null);
    try{
      const hint=await getBuildingRegisterHint(organizationId,complexId);
      if(!hint?.available){setNotice("공식 지번을 자동 확인할 수 없습니다. 직접 입력해 주세요.");return;}
      const {sigunguCd,bjdongCd,platGbCd,bun,ji,lotAddress}=hint.parcel;
      setParcel({sigunguCd,bjdongCd,platGbCd,bun,ji});
      setNotice("K-apt 지번 후보: "+lotAddress+" · 실제 단지 및 복수 필지 여부를 확인해 주세요.");
    }catch(e){setError(explainError(e));}finally{finish();}
  }
  async function lookup(e){
    e.preventDefault();if(!start("lookup"))return;
    setError("");setNotice("");setPreview(null);setSelected([]);setConfirmed(false);setLookedUp(null);setPage(0);setExcludedPage(0);setBuildingFilter("");
    try{
      const params={...parcel,bun:parcel.bun.padStart(4,"0"),ji:parcel.ji.padStart(4,"0")};
      const data=await previewBuildingUnits(organizationId,complexId,params);
      setPreview(data);setLookedUp(params);setSelected(data.units.map(x=>x.recordId));
    }catch(e){setError(explainError(e));}finally{finish();}
  }
  async function commit(){
    if(blockers.length||!start("commit"))return;
    setError("");setNotice("");
    try{
      const result=await commitBuildingUnits(organizationId,complexId,Number(effectiveRoot),lookedUp,selected);
      setResult(result);
      setNotice(`동 ${result.createdBuildings}개, 세대 ${result.createdUnits}개 신규 등록 · 기존 세대 ${result.existingUnits}개 유지`);
      setPreview(null);setSelected([]);setConfirmed(false);onImported();
    }catch(e){setError(explainError(e));}finally{finish();}
  }
  return <Card className="mb-5 grid gap-3 p-6">
    <h2 className="font-bold">건축물대장 동·호수 가져오기</h2>
    <Alert>선택 단지: {complex?.display_name} · {complex?.road_address || "주소 확인 필요"}. 공식 지번을 입력하고 조회 주소와 단지가 일치하는지 확인하세요. 복수 필지는 각각 조회합니다.</Alert>
    <Button type="button" variant="secondary" disabled={busy || !complex?.kapt_code} onClick={autoFill}>K-apt 공식 지번 가져오기</Button>
    {!roots.length && <Alert>먼저 아래 위치 계층에서 COMPLEX 루트 위치를 등록하세요.</Alert>}
    <form className="grid gap-3 sm:grid-cols-3" onSubmit={lookup}>
      {[["sigunguCd","시군구코드",5],["bjdongCd","법정동코드 뒤 5자리",5],["bun","지번 본번",4],["ji","지번 부번",4]].map(([key,label,len])=><Field key={key} label={label} value={parcel[key]} disabled={busy} required inputMode="numeric" maxLength={len} pattern={len===5?"[0-9]{5}":"[0-9]{1,4}"} onChange={e=>change(key,e.target.value.replace(/[^0-9]/g,""))}/>)}
      <Field label="대지 구분" as="select" value={parcel.platGbCd} disabled={busy} onChange={e=>change("platGbCd",e.target.value)}><option value="0">대지</option><option value="1">산</option><option value="2">블록</option></Field>
      <Button type="submit" disabled={busy||locationsLoading||!!locationsError||!roots.length}>공식 전유부 조회</Button>
    </form>
    {busy&&<Alert>{operation==="commit"?"등록 중: 공식 데이터 재조회 및 DB 저장 중입니다.":operation==="hint"?"K-apt 지번 조회 중입니다.":"전유부 데이터를 조회하고 분류 중입니다."}</Alert>}
    {error&&<Alert tone="error">{error}</Alert>}
    {notice&&<Alert tone="success">{notice}</Alert>}
    {preview&&<>
      <p>원본 {preview.sourceCount}건 · 주거 후보 {preview.units.length}건 · 비주거 후보 {preview.nonResidentialCount}건 · 검토 필요 {preview.reviewCount}건</p>
      <Alert>건물명 및 동·호수 형식으로 분류한 후보입니다. 공식 용도 확인 결과가 아니므로 실제 주거 세대 및 단지 일치 여부를 반드시 확인하세요.</Alert>
      {preview.excludedUnits?.length>0&&<details className="grid gap-2">
        <summary>제외 및 검토 필요 항목 {preview.excludedCount}건</summary>
        {preview.excludedUnits.slice(excludedPage*50,(excludedPage+1)*50).map(x=><div key={x.recordId} className="border-t py-2 text-sm">{x.buildingLabel} · {x.buildingName||"동명 없음"} · {x.unitName||"호명 없음"} — {x.reason}</div>)}
        <div className="flex items-center gap-3"><Button type="button" variant="secondary" disabled={busy||excludedPage===0} onClick={()=>setExcludedPage(v=>v-1)}>이전</Button><span>{excludedPage+1} 페이지</span><Button type="button" variant="secondary" disabled={busy||(excludedPage+1)*50>=preview.excludedUnits.length} onClick={()=>setExcludedPage(v=>v+1)}>다음</Button></div>
        <p className="text-sm">검토 필요 항목은 자동 등록하지 않습니다. 실제 세대임을 확인한 뒤 기존 위치 등록 기능으로 보완하세요.</p>
      </details>}
      <Field label="등록할 단지 루트" hint={roots.length===1?"활성 루트가 하나여서 자동 선택되었습니다.":undefined} as="select" value={effectiveRoot} disabled={busy||roots.length===1} onChange={e=>{setRoot(e.target.value);setConfirmed(false);}}><option value="">선택</option>{roots.map(x=><option key={x.id} value={x.id}>{x.name}</option>)}</Field>
      <div className="flex flex-wrap gap-2"><Button type="button" variant="secondary" disabled={busy} onClick={()=>{setSelected(preview.units.map(u=>u.recordId));setConfirmed(false);}}>전체 후보 선택</Button><Button type="button" variant="secondary" disabled={busy} onClick={()=>{setSelected([]);setConfirmed(false);}}>선택 해제</Button></div>
      <div className="grid gap-3 rounded-xl border border-fixops-border p-3">
        <Field label="동별 조회" as="select" value={buildingFilter} disabled={busy} onChange={e=>{setBuildingFilter(e.target.value);setPage(0);}}>
          <option value="">전체 동 ({groups.length}개)</option>
          {groups.map(g=><option key={g.code} value={g.code}>{g.code}동 · {g.units.length}세대</option>)}
        </Field>
        <div className="flex max-h-36 flex-wrap gap-2 overflow-auto">
          {groups.map(g=>{
            const count=g.units.filter(x=>selectedSet.has(x.recordId)).length;
            return <div key={g.code} className="flex items-center gap-2 rounded-lg border border-fixops-border px-3 py-2 text-sm">
              <button type="button" disabled={busy} className="font-semibold" onClick={()=>{setBuildingFilter(g.code);setPage(0);}}>{g.code}동 ({count}/{g.units.length})</button>
              <button type="button" disabled={busy} className="text-fixops-primary" onClick={()=>toggleGroup(g,count!==g.units.length)}>{count===g.units.length?"해제":"선택"}</button>
            </div>;
          })}
        </div>
        <div className="max-h-[420px] overflow-y-auto rounded-lg border border-fixops-border">
          {visibleUnits.slice(page*50,(page+1)*50).map((unit,i,rows)=><div key={unit.recordId}>
            {(i===0||rows[i-1].buildingNo!==unit.buildingNo)&&<h3 className="sticky top-0 bg-fixops-background px-3 py-2 text-sm font-bold">{unit.buildingNo}동</h3>}
            <label className="flex gap-3 border-t border-fixops-border px-3 py-2 text-sm">
              <input type="checkbox" disabled={busy} checked={selectedSet.has(unit.recordId)} onChange={e=>{setSelected(v=>e.target.checked?[...new Set([...v,unit.recordId])]:v.filter(x=>x!==unit.recordId));setConfirmed(false);}}/>
              <span><strong>{unit.buildingNo}동 {unit.unitNo}호</strong><span className="ml-2 text-xs text-fixops-muted">{unit.buildingLabel} · {unit.roadAddress||unit.address}</span></span>
            </label>
          </div>)}
          {!visibleUnits.length&&<p className="p-4 text-sm text-fixops-muted">표시할 주거 후보가 없습니다.</p>}
        </div>
        <div className="flex items-center gap-3"><Button variant="secondary" disabled={busy||page===0} onClick={()=>setPage(v=>v-1)}>이전</Button><span>{page+1} / {Math.max(1,Math.ceil(visibleUnits.length/50))} 페이지</span><Button variant="secondary" disabled={busy||(page+1)*50>=visibleUnits.length} onClick={()=>setPage(v=>v+1)}>다음</Button></div>
      </div>
      <div className="sticky bottom-0 z-10 grid gap-2 rounded-xl border border-fixops-border bg-white p-4 shadow-lg">
        <p className="font-bold">선택 {selected.length} / {preview.units.length}세대 · {roots.find(x=>String(x.id)===effectiveRoot)?.name||"단지 루트 미선택"}</p>
        <label className="flex items-start gap-2 text-sm"><input type="checkbox" checked={confirmed&&confirmedRoot===effectiveRoot} disabled={busy||!effectiveRoot||!selected.length} onChange={e=>{setConfirmed(e.target.checked);setConfirmedRoot(effectiveRoot);}}/> 선택한 모든 전유부가 현재 관리 단지에 속하는 실제 주거 세대임을 확인했습니다.</label>
        {blockers.length>0&&!busy&&<p className="text-sm text-fixops-muted" role="status">등록 조건: {blockers.join(" · ")}</p>}
        {operation==="commit"&&<p role="status" className="text-sm">공식 데이터 재검증 및 저장 중…</p>}
        {error&&<Alert tone="error">{error}</Alert>}
        <Button disabled={busy||blockers.length>0} onClick={commit}>{operation==="commit"?"등록 처리 중…":"선택 "+selected.length+"세대 등록"}</Button>
      </div>
    </>}
    <Modal open={!!result} onClose={()=>setResult(null)} title="세대 등록 완료" description={complex?.display_name} footer={<Button onClick={()=>setResult(null)}>확인</Button>}>
      {result&&<dl className="grid grid-cols-2 gap-3 text-sm"><dt>신규 동</dt><dd>{result.createdBuildings}개</dd><dt>신규 세대</dt><dd>{result.createdUnits}세대</dd><dt>기존 세대 유지</dt><dd>{result.existingUnits}세대</dd></dl>}
      <p className="mt-4 text-sm text-fixops-muted">위치 목록을 갱신합니다. 조회 오류가 있으면 위치 계층에서 새로고침하세요.</p>
    </Modal>
  </Card>;
}
