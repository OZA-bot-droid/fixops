import api, { unwrap } from "../../api/client";

const base = "/resident-enrollments";

export const applyResident = async ({ complexId, buildingNo, unitNo, contact }) =>
  unwrap(await api.post(base, { complexId, buildingNo, unitNo, contact }));

export const verifyResident = async (id, code) =>
  unwrap(await api.post(`${base}/${id}/verify`, { code }));

export const myResidentEnrollments = async (page = 0, size = 20, signal) =>
  unwrap(await api.get(`${base}/mine`, { params: { page, size }, signal }));

export const pendingResidentEnrollments = async (complexId, page = 0, size = 20, signal) =>
  unwrap(await api.get(`${base}/pending`, { params: { complexId, page, size }, signal }));

export const decideResident = async (id, approve) =>
  unwrap(await api.patch(`${base}/${id}/decision`, { approve }));

export const searchResidentComplexes = async (q = "", page = 0, size = 20, signal) =>
  unwrap(await api.get(`${base}/complexes`, { params: { q, page, size }, signal }));

const registrationBase = complexId => `/complexes/${complexId}/resident-registration-codes`;
export const listRegistrationEntries = async (complexId, page = 0, size = 20, signal) =>
  unwrap(await api.get(registrationBase(complexId), { params: { page, size }, signal }));
export const issueRegistrationCode = async (complexId, rosterEntryId) =>
  unwrap(await api.post(registrationBase(complexId), { rosterEntryId }));
export const revokeRegistrationCode = async (complexId, codeId) =>
  api.delete(`${registrationBase(complexId)}/${codeId}`);
export const redeemRegistrationCode = async (complexId, code) =>
  unwrap(await api.post(`${base}/redeem-code`, { complexId, code }));

export const getResidentVerificationMethods = async (signal) =>
  unwrap(await api.get(`${base}/verification-methods`, { signal }));
