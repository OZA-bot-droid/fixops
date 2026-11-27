// Account-level access uses all active memberships; unknown claims fail closed.
const knownRoles = new Set(["ADMIN","TECHNICIAN","USER"]);
export function verifiedRoles(user) {
  if (!user || user.platformAdmin !== false || user.emailVerified !== true ||
      !Array.isArray(user.memberships) ||
      user.memberships.some(m => !m || !knownRoles.has(m.role))) return null;
  return user.memberships.map(m => m.role);
}
export function canApplyOrganization(user) {
  const roles = verifiedRoles(user);
  return roles !== null && (roles.length === 0 || roles.includes("ADMIN"));
}
export function canJoinOrganization(user) { return verifiedRoles(user) !== null; }
export function canEnrollResident(user) {
  const roles = verifiedRoles(user);
  return roles !== null && (roles.length === 0 || roles.includes("USER"));
}
export function eligibleBuildingOrganizations(user, applications = []) {
  const result = new Map();
  if (verifiedRoles(user) === null) return result;
  for (const m of user.memberships) {
    if (m.role === "ADMIN" && m.organizationKind === "BUILDING" && m.organizationId != null)
      result.set(String(m.organizationId), {id:m.organizationId,name:m.organizationName || "조직 #"+m.organizationId,status:"ACTIVE"});
  }
  for (const o of Array.isArray(applications) ? applications : []) {
    if (o?.kind === "BUILDING" && o.status === "PENDING_APPROVAL" && o.id != null &&
        o.applicantId != null && user.id != null && String(o.applicantId) === String(user.id) &&
        !result.has(String(o.id))) result.set(String(o.id),o);
  }
  return result;
}
export function canUseOrganizationFeature(user, scope, {roles,buildingOnly=false}={}) {
  if (verifiedRoles(user) === null || scope?.activeOrganizationId == null) return false;
  const m=user.memberships.find(m=>String(m.organizationId)===String(scope.activeOrganizationId) &&
    m.role===scope.activeRole && m.organizationKind===scope.activeOrganizationKind);
  return Boolean(m && (!roles || roles.includes(m.role)) && (!buildingOnly || m.organizationKind==="BUILDING"));
}
export function canSeeNavigation(user,item,scope,applications) {
  if (item.url==="/member") return Boolean(user);
  if (item.onboarding) return canApplyOrganization(user);
  if (item.join) return canJoinOrganization(user);
  if (item.resident) return canEnrollResident(user);
  if (item.buildingManagement) return eligibleBuildingOrganizations(user,applications).size>0;
  return canUseOrganizationFeature(user,scope,item);
}

const targets = Object.freeze({
  resident: "/residents/enroll",
  member: "/organizations/join",
  organization: "/organizations/apply",
  invitation: "/organizations/join",
});
export function onboardingIntent(search) {
  const value = new URLSearchParams(search).get("onboarding");
  return Object.hasOwn(targets, value) ? value : "";
}
export function onboardingQuery(intent) {
  return Object.hasOwn(targets, intent) ? "?onboarding=" + intent : "";
}
export function onboardingDestination(user, intent) {
  if (!user || !Object.hasOwn(targets, intent)) return "/member";
  if (user.emailVerified !== true) return "/email-verification" + onboardingQuery(intent);
  if (!canJoinOrganization(user)) return "/member";
  if (intent === "organization" && !canApplyOrganization(user)) return "/organizations/join";
  if (intent === "resident" && !canEnrollResident(user)) return "/organizations/join";
  return targets[intent];
}
