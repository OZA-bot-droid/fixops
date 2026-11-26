import api, { unwrap } from "../../api/client";

const base = (complexId) => `/complexes/${complexId}/roster-imports`;

export async function uploadRoster(complexId, mode, file) {
  const body = new FormData();
  body.append("file", file);
  body.append("mode", mode);
  return unwrap(await api.post(base(complexId), body, { timeout: 120000 }));
}

export const previewRoster = async (complexId, jobId, page = 0, size = 20, signal) =>
  unwrap(await api.get(`${base(complexId)}/${jobId}`, { params: { page, size }, signal }));

export const commitRoster = async (complexId, jobId, acknowledgeMissing = false) =>
  unwrap(await api.post(`${base(complexId)}/${jobId}/commit`, null, { params: { acknowledgeMissing }, timeout: 120000 }));
