import { createContext, useContext, useState, useEffect } from "react";
import { useAuth } from "./AuthProvider";
import api, { unwrap } from "../../api/client";
import { explainError } from "../../api/errors";

const Context = createContext(null);
const roles = new Set(["ADMIN", "TECHNICIAN", "USER"]);

function ComplexScope({ active, children, ...organization }) {
  const [result, setResult] = useState({ items: [], loading: true, error: "" });
  const [selected, setSelected] = useState(null);
  const [revision, setRevision] = useState(0);
  const orgId = active?.organizationId;
  const building = active?.organizationKind === "BUILDING";
  useEffect(() => {
    if (!building || orgId == null) return;
    const controller = new AbortController();
    setResult({ items: [], loading: true, error: "" });
    api.get(`/organizations/${orgId}/complexes/mine`, { signal: controller.signal })
      .then(unwrap)
      .then(items => {
        if (!controller.signal.aborted) setResult({ items: Array.isArray(items) ? items : [], loading: false, error: "" });
      })
      .catch(e => {
        if (!controller.signal.aborted) setResult({ items: [], loading: false, error: explainError(e) });
      });
    return () => controller.abort();
  }, [building, orgId, revision]);
  const complexes = building ? result.items : [];
  const complex = selected == null
    ? (complexes.length === 1 ? complexes[0] : null)
    : complexes.find(c => String(c.id) === selected) ?? null;
  function selectComplex(id) {
    const value = String(id ?? "");
    if (value && !complexes.some(c => String(c.id) === value)) return false;
    setSelected(value);
    return true;
  }
  return <Context.Provider value={{ ...organization,
    activeOrganizationKind: active?.organizationKind ?? null,
    activeOrganizationName: active?.organizationName ?? null,
    complexes, activeComplexId: complex?.id ?? null,
    complexesLoading: building && result.loading,
    complexesError: building ? result.error : "",
    selectComplex,
    reloadComplexes: () => { setSelected(null); setResult({ items: [], loading: true, error: "" }); setRevision(v => v + 1); },
  }}>{children}</Context.Provider>;
}

function OrganizationScope({ children }) {
  const { user } = useAuth();
  const [selectedId, setSelectedId] = useState(null);
  const [selectionMade, setSelectionMade] = useState(false);
  const memberships = (Array.isArray(user?.memberships) ? user.memberships : [])
    .filter(m => m?.organizationId != null && m?.membershipId != null && roles.has(m.role));
  const active = selectionMade
    ? memberships.find(m => String(m.organizationId) === selectedId) ?? null
    : memberships.length === 1 ? memberships[0] : null;
  function selectOrganization(id) {
    const value = String(id ?? "");
    if (value && !memberships.some(m => String(m.organizationId) === value)) return false;
    setSelectedId(value || null);
    setSelectionMade(true);
    return true;
  }
  return <ComplexScope key={`${active?.organizationId ?? "none"}:${active?.membershipId ?? "none"}:${active?.role ?? "none"}:${active?.organizationKind ?? "none"}`}
    active={active} memberships={memberships} activeOrganizationId={active?.organizationId ?? null}
    activeMembershipId={active?.membershipId ?? null} activeRole={active?.role ?? null}
    selectOrganization={selectOrganization}>{children}</ComplexScope>;
}
export function OrganizationProvider({ children }) {
  const { user, loggedIn } = useAuth();
  return <OrganizationScope key={loggedIn ? String(user?.id) : "anonymous"}>{children}</OrganizationScope>;
}
export function useOrganization() {
  const value = useContext(Context);
  if (!value) throw new Error("OrganizationProvider required");
  return value;
}
