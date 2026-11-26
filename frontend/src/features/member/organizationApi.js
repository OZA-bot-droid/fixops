import api, { unwrap } from "../../api/client";

export const searchOrganizations = async (q = "", page = 0, size = 20, signal) =>
  unwrap(await api.get("/organizations", { params: { q, page, size }, signal }));
export const requestJoin = async (id) =>
  unwrap(await api.post(`/organizations/${id}/join-requests`));
export const myJoinRequests = async (id, page = 0, size = 20, signal) =>
  unwrap(await api.get(`/organizations/${id}/join-requests/mine`, { params: { page, size }, signal }));
export const pendingJoinRequests = async (id, page = 0, size = 20, signal) =>
  unwrap(await api.get(`/organizations/${id}/join-requests`, { params: { page, size }, signal }));
export const decideJoinRequest = async (id, requestId, approve) =>
  unwrap(await api.patch(`/organizations/${id}/join-requests/${requestId}/decision`, { approve }));
export const inviteMember = async (id, email, role) =>
  unwrap(await api.post(`/organizations/${id}/invitations`, { email, role }));
export const acceptInvitation = async (token) =>
  unwrap(await api.post("/invitations/accept", { token }));
export const changeMemberRole = async (id, memberId, role) =>
  unwrap(await api.patch(`/organizations/${id}/members/${memberId}/role`, { role }));
export const deactivateMember = async (id, memberId) =>
  unwrap(await api.delete(`/organizations/${id}/members/${memberId}`));
export const leaveOrganization = async (id) =>
  unwrap(await api.delete(`/organizations/${id}/members/me`));

export const listMembers = async (id, page = 0, size = 20, signal) =>
  unwrap(await api.get(`/organizations/${id}/members`, { params: { page, size }, signal }));
