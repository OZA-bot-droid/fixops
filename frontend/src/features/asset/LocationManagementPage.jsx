import { useEffect, useState } from "react";
import { useOrganization } from "../member/OrganizationProvider";
import { Alert, Button, Card, StatusBadge } from "../../components/ui";
import { PageHeader } from "../../components/layout/AppLayout";
import { explainError } from "../../api/errors";
import { listLocations, createLocation, updateLocation } from "./locationApi";
import { allowedTypes, LOCATION_LABELS } from "./locationRules";
import LocationTree from "./LocationTree";
import LocationFormModal from "./LocationFormModal";
import BuildingUnitImport from "./BuildingUnitImport";

function Workspace({ organizationId, complexId, kind, writable, complex }) {
  const [items, setItems] = useState([]);
  const [selectedId, setSelectedId] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [revision, setRevision] = useState(0);
  const [form, setForm] = useState(null);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true); setError("");
    listLocations(organizationId, complexId, controller.signal).then(data => {
      if (!controller.signal.aborted) {
        setItems(data);
        setSelectedId(old => data.some(x => String(x.id) === String(old)) ? old : data[0]?.id ?? null);
      }
    }).catch(e => {
      if (!controller.signal.aborted) { setItems([]); setError(explainError(e)); }
    }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [organizationId, complexId, revision]);
  const selected = items.find(x => String(x.id) === String(selectedId));
  const parent = selected && items.find(x => x.id === selected.parentId);
  async function save(values) {
    if (!writable || !form) throw new Error("ACCESS_DENIED");
    const saved = form.item
      ? await updateLocation(organizationId, complexId, form.item.id, values)
      : await createLocation(organizationId, complexId, values);
    setSelectedId(saved.id); setForm(null); setNotice("위치 정보를 저장했습니다."); setRevision(v => v + 1);
  }
  return <>
    {notice && <div className="mb-4"><Alert tone="success">{notice}</Alert></div>}
    {error && <div className="mb-4"><Alert tone="error">{error}</Alert></div>}
    {writable && kind === "BUILDING" && <BuildingUnitImport organizationId={organizationId} complexId={complexId} complex={complex} locationsLoading={loading} locationsError={error} roots={items.filter(x => x.locationType === "COMPLEX" && x.status === "ACTIVE" && x.parentId == null)} onImported={() => setRevision(v => v+1)} />}
    <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
      <Card className="grid content-start gap-4 p-6">
        <div className="flex flex-wrap items-center justify-between gap-2"><h2 className="font-bold">위치 계층</h2><div className="flex flex-wrap gap-2"><Button variant="secondary" disabled={loading || !!form} onClick={() => { setNotice(""); setRevision(v => v + 1); }}>새로고침</Button>{writable && <Button disabled={loading || !!error || !!form} onClick={() => setForm({ parent: null })}>루트 위치 등록</Button>}</div></div>
        {loading ? <p role="status">위치 목록 조회 중…</p> : items.length ? <LocationTree items={items} selectedId={selectedId} onSelect={setSelectedId} /> : !error && <p className="text-sm text-fixops-muted">등록된 위치가 없습니다.</p>}
      </Card>
      <Card className="grid content-start gap-4 p-6">
        <h2 className="font-bold">위치 상세</h2>
        {!selected || loading ? <p className="text-sm text-fixops-muted">위치를 선택해 주세요.</p> : <>
          <dl className="grid grid-cols-[100px_1fr] gap-3 text-sm">
            <dt>위치명</dt><dd className="break-words">{selected.name}</dd>
            <dt>유형</dt><dd>{LOCATION_LABELS[selected.locationType]}</dd>
            <dt>코드</dt><dd className="break-words">{selected.code}</dd>
            <dt>상위 위치</dt><dd>{parent?.name || "—"}</dd>
            <dt>상태</dt><dd><StatusBadge status={selected.status} /></dd>
          </dl>
          {writable ? <div className="flex flex-wrap gap-2"><Button variant="secondary" onClick={() => setForm({ item: selected })}>위치 수정</Button><Button disabled={!allowedTypes(kind, selected).length} onClick={() => setForm({ parent: selected })}>하위 위치 추가</Button></div> : <Alert>현장 기술자는 위치 정보를 조회할 수 있습니다.</Alert>}
        </>}
      </Card>
    </div>
    {form && writable && <LocationFormModal kind={kind} parent={form.parent} item={form.item} onClose={() => setForm(null)} onSave={save} />}
  </>;
}
export default function LocationManagementPage() {
  const { activeOrganizationId, activeOrganizationKind, activeOrganizationName, activeComplexId, activeRole, complexes } = useOrganization();
  const building = activeOrganizationKind === "BUILDING";
  const complex = complexes.find(x => String(x.id) === String(activeComplexId));
  const valid = activeOrganizationId != null && (activeOrganizationKind === "FACTORY" || (building && complex && ["ADMIN","TECHNICIAN"].includes(complex.access_scope)));
  const writable = activeRole === "ADMIN" && (!building || complex?.access_scope === "ADMIN");
  return <>
    <PageHeader title="설비·위치 관리" description={[activeOrganizationName, building ? complex?.display_name : null].filter(Boolean).join(" / ")} />
    {!valid ? <Alert>접근 권한이 있는 업무 조직 및 단지를 선택해 주세요.</Alert> : <Workspace key={JSON.stringify([activeOrganizationId, building ? activeComplexId : null, activeRole, writable])} organizationId={activeOrganizationId} complexId={building ? activeComplexId : null} kind={activeOrganizationKind} writable={writable} complex={complex} />}
  </>;
}
