import api from "../../api/client.js";

export async function sendChatMessage(message, organizationId) {

  const response = await api.post(
    "/ai/chat",
    { message },
    {
      timeout: 60000,
      headers: {
        "X-Organization-Id": organizationId,
      },
    },
  );

  const body = response.data?.data ?? response.data;

  return body.answer;
}

export async function sendRagQuery({
  query,
  organizationId,
  complexId,
}) {
  const response = await api.post(
    "/ai/rag/query",
    {
      query,
      complex_id: complexId,
      top_k: 3,
    },
    {
      headers: {
        "X-Organization-Id": organizationId,
      },
      timeout: 60000,
    },
  );

  return response.data;
}

export async function uploadRagDocument({
  file,
  organizationId,
  complexId,
}) {
  const formData = new FormData();

  formData.append("file", file);

  if (complexId != null) {
    formData.append("complex_id", String(complexId));
  }

  const response = await api.post(
    "/ai/documents/upload",
    formData,
    {
      headers: {
        "X-Organization-Id": organizationId,
      },
      timeout: 180000,
    },
  );

  return response.data;
}

export async function getRagDocuments({
  organizationId,
  complexId,
}) {
  const params = complexId == null? {} : { complex_id: complexId }

  const response = await api.get("/ai/documents", {
    params,
    headers: {
      "X-Organization-Id": organizationId,
    },
  });

  return response.data;
}