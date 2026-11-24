import api from "../../api/client.js";

export async function sendChatMessage(message) {
  const response = await api.post("/ai/chat", { message }, { timeout: 60000 });

  const body = response.data;
  const answer = body?.answer ?? body?.data?.answer;

  if (typeof answer !== "string") {
    throw new Error("AI 응답 형식이 올바르지 않습니다.");
  }

  return answer;
}
