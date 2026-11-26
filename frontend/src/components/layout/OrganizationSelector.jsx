import { useOrganization } from "../../features/member/OrganizationProvider";
export default function OrganizationSelector() {
  const { memberships, activeOrganizationId, selectOrganization } = useOrganization();
  return <label className="grid gap-1 text-xs text-fixops-muted">
    업무 조직
    <select aria-label="업무 조직 선택" className="rounded-lg border border-fixops-border bg-white px-3 py-2 text-sm text-fixops-text"
      value={activeOrganizationId == null ? "" : String(activeOrganizationId)}
      onChange={e => selectOrganization(e.target.value)}>
      <option value="">{memberships.length ? "조직 선택" : "소속 조직 없음"}</option>
      {memberships.map(m => <option key={m.membershipId} value={String(m.organizationId)}>{m.organizationName || "조직 #" + m.organizationId} · {m.organizationKind === "BUILDING" ? "공동주택" : m.organizationKind === "FACTORY" ? "공장" : "유형 미확인"} · {m.role}</option>)}
    </select>
  </label>;
}
