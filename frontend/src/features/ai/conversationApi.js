import api from "../../api/client.js";

const BASE = "/ai/conversations";

function orgHeaders(organizationId) {
  return {
    "X-Organization-Id": organizationId,
  };
}

export async function fetchConversations(organizationId) {
  const response = await api.get(BASE, {
    headers: orgHeaders(organizationId),
  });

  return response.data;
}

export async function createConversation({
  organizationId,
  complexId,
}) {
  const response = await api.post(
    BASE,
    {
      title: "새 대화",
      complex_id: complexId,
    },
    {
      headers: orgHeaders(organizationId),
    },
  );

  return response.data;
}

export async function fetchConversationDetail({
  organizationId,
  conversationId,
}) {
  const response = await api.get(
    `${BASE}/${conversationId}`,
    {
      headers: orgHeaders(organizationId),
    },
  );

  return response.data;
}

export async function sendConversationMessage({
  organizationId,
  conversationId,
  message,
}) {
  const response = await api.post(
    `${BASE}/${conversationId}/messages`,
    {
      message,
      top_k: 3,
    },
    {
      headers: orgHeaders(organizationId),
      timeout: 180000,
    },
  );

  return response.data;
}

export async function deleteConversation({
  organizationId,
  conversationId,
}) {
  await api.delete(
    `${BASE}/${conversationId}`,
    {
      headers: orgHeaders(organizationId),
    },
  );
}