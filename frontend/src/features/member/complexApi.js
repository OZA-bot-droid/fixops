import api, { unwrap } from "../../api/client";

const KAPT_TIMEOUT = 25000;

export const getKaptCatalogStatus = async (signal) =>
  unwrap(await api.get("/complexes/catalog/status", { signal }));

export const searchComplexes = async (regionCode, q = "", page = 1, size = 20, signal) =>
  unwrap(await api.get("/complexes/search", { params: { regionCode, q, page, size }, timeout: KAPT_TIMEOUT, signal }));
export const getKaptComplex = async (kaptCode, signal) =>
  unwrap(await api.get(`/complexes/kapt/${encodeURIComponent(kaptCode)}`, { timeout: KAPT_TIMEOUT, signal }));
export const registerComplex = async (organizationId, data) =>
  unwrap(await api.post(`/organizations/${organizationId}/complexes`, {
    kaptCode: data.kaptCode || null, displayName: data.displayName, roadAddress: data.roadAddress,
  }, { timeout: data.kaptCode ? KAPT_TIMEOUT : 10000 }));
export const myComplexes = async (organizationId, signal) =>
  unwrap(await api.get(`/organizations/${organizationId}/complexes/mine`, { signal }));
export const pendingComplexes = async (page = 0, size = 20, signal) =>
  unwrap(await api.get("/platform/complex-managements/pending", { params: { page, size }, signal }));
export const addressCandidates = async (managementId, page = 0, size = 20, signal) =>
  unwrap(await api.get(`/platform/complex-managements/${managementId}/address-candidates`, { params: { page, size }, signal }));
export const decideComplex = async (managementId, approve, reason, confirmDistinctAddress = false) =>
  unwrap(await api.patch(`/platform/complex-managements/${managementId}/decision`, { approve, reason, confirmDistinctAddress }));
export const changeComplexAccess = async (organizationId, complexId, memberId, grant) =>
  unwrap(await api.patch(`/organizations/${organizationId}/complexes/${complexId}/members/${memberId}/access`, { grant }));

export const listComplexManagements = async (organizationId, page = 0, size = 20, signal) =>
  unwrap(await api.get(`/organizations/${organizationId}/complex-managements`, { params: { page, size }, signal }));

export const listComplexMembers = async (organizationId, complexId, page = 0, size = 20, signal) =>
  unwrap(await api.get(`/organizations/${organizationId}/complexes/${complexId}/members`, { params: { page, size }, signal }));
