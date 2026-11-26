import { useOrganization } from "../../features/member/OrganizationProvider";

export default function ComplexSelector() {
  const { activeOrganizationKind, complexes, activeComplexId, complexesLoading,
    complexesError, selectComplex, reloadComplexes } = useOrganization();
  if (activeOrganizationKind !== "BUILDING") return null;
  return (
    <div className="grid gap-1 text-xs text-fixops-muted">
      <label className="grid gap-1">
        관리 단지
        <select aria-label="관리 단지 선택"
          className="max-w-[240px] rounded-lg border border-fixops-border bg-white px-3 py-2 text-sm text-fixops-text"
          value={activeComplexId == null ? "" : String(activeComplexId)}
          disabled={complexesLoading || !!complexesError || !complexes.length}
          onChange={e => selectComplex(e.target.value)}>
          <option value="">{complexesLoading ? "조회 중…" : complexesError ? "조회 실패" : complexes.length ? "단지 선택" : "접근 가능한 단지 없음"}</option>
          {complexes.map(c => <option key={c.id} value={String(c.id)}>{c.display_name || c.displayName || "단지 #" + c.id}</option>)}
        </select>
      </label>
      {complexesError && <span role="alert" className="text-fixops-danger">{complexesError}</span>}
      {!complexesLoading && <button type="button" onClick={reloadComplexes}
        className="self-start text-left text-fixops-primary underline">단지 목록 다시 조회</button>}
    </div>
  );
}
