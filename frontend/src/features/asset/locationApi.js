import api from "../../api/client";

const scope = (organizationId, complexId) => ({
  organizationId,
  ...(complexId != null ? { complexId } : {}),
});

export async function listLocations(organizationId, complexId, signal) {
  const { data } = await api.get("/locations", { params: scope(organizationId, complexId), signal });
  if (!Array.isArray(data)) throw new Error("INVALID_RESPONSE");
  return data;
}
export async function createLocation(organizationId, complexId, values) {
  const { data } = await api.post("/locations", { ...values, organizationId, complexId: complexId ?? null });
  return data;
}
export async function updateLocation(organizationId, complexId, id, values) {
  const { data } = await api.put(`/locations/${id}`, values, { params: scope(organizationId, complexId) });
  return data;
}

export async function previewBuildingUnits(organizationId,complexId,parcel,signal){
  return (await api.post("/locations/building-register/preview",{organizationId,complexId,parcel},{timeout:45000,signal})).data;
}
export async function commitBuildingUnits(organizationId,complexId,rootId,parcel,selectedIds){
  return (await api.post("/locations/building-register/commit",{organizationId,complexId,rootId,parcel,selectedIds,confirmedParcel:true},{timeout:55000})).data;
}

export async function getBuildingRegisterHint(organizationId,complexId){
  const {data}=await api.get(`/organizations/${organizationId}/complexes/${complexId}/building-register-hint`,{timeout:25000});
  return data.data;
}
