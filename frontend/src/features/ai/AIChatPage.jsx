import { useEffect, useRef, useState } from "react";
import { PageHeader } from "../../components/layout/AppLayout";
import { sendChatMessage } from "./chatApi";

const suggestions = [
  "급수 펌프 이상 진단",
  "모터 정기 점검 절차",
  "방문 예약 가능 시간",
  "부품 재고 조회",
];

const welcomeMessage = {
  id: "welcome",
  role: "assistant",
  content:
    "안녕하세요! 어떤 설비 점검이 필요하신가요?\n설비 상태, 수리 이력, 예약 일정을 확인할 수 있어요.",
};

function createConversation(id) {
  return {
    id,
    title: "새 대화",
    messages: [welcomeMessage],
  };
}

function getErrorMessage(error) {
  if (error.response?.status === 401) {
    return "인증이 필요하거나 AI 인증 연동이 아직 준비되지 않았습니다.";
  }

  if (error.response?.status === 403) {
    return "이 AI 기능에 접근할 권한이 없습니다.";
  }

  if (error.response?.status === 429) {
    return "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.";
  }

  if (error.response?.status >= 500) {
    return "AI 서버에서 오류가 발생했습니다.";
  }

  if (error.code === "ECONNABORTED") {
    return "AI 응답 시간이 초과되었습니다.";
  }

  if (!error.response) {
    return "서버에 연결할 수 없습니다.";
  }

  return "요청을 처리하지 못했습니다.";
}

export default function AIChatPage() {
  const nextId = useRef(1);
  const messagesRef = useRef(null);

  const [conversations, setConversations] = useState([
    createConversation("chat-0"),
  ]);
  const [activeId, setActiveId] = useState("chat-0");
  const [message, setMessage] = useState("");
  const [sending, setSending] = useState(false);
  const [error, setError] = useState("");

  const activeConversation = conversations.find(
    (conversation) => conversation.id === activeId,
  );

  const messages = activeConversation?.messages ?? [];

  useEffect(() => {
    const container = messagesRef.current;

    if (!container) return;

    container.scrollTop = container.scrollHeight;
  }, [messages.length, sending, activeId]);

  function addMessage(conversationId, item, title) {
    setConversations((previous) =>
      previous.map((conversation) =>
        conversation.id === conversationId
          ? {
              ...conversation,
              title: title ?? conversation.title,
              messages: [...conversation.messages, item],
            }
          : conversation,
      ),
    );
  }

  function startNewChat() {
    if (sending) return;

    const id = `chat-${nextId.current++}`;

    setConversations((previous) => [createConversation(id), ...previous]);
    setActiveId(id);
    setMessage("");
    setError("");
  }

  async function handleSubmit(event) {
    event.preventDefault();

    const text = message.trim();

    if (!text || sending || !activeConversation) return;

    const conversationId = activeId;
    const firstMessage = messages.length === 1;

    setSending(true);
    setError("");
    setMessage("");

    addMessage(
      conversationId,
      {
        id: crypto.randomUUID(),
        role: "user",
        content: text,
      },
      firstMessage ? text.slice(0, 24) : undefined,
    );

    try {
      const answer = await sendChatMessage(text);

      addMessage(conversationId, {
        id: crypto.randomUUID(),
        role: "assistant",
        content: answer,
      });
    } catch (requestError) {
      setError(getErrorMessage(requestError));
    } finally {
      setSending(false);
    }
  }

  function handleKeyDown(event) {
    if (
      event.key === "Enter" &&
      !event.shiftKey &&
      !event.nativeEvent.isComposing
    ) {
      event.preventDefault();
      event.currentTarget.form?.requestSubmit();
    }
  }

  return (
    <section>
      <PageHeader
        title="AI 점검·채팅센터"
        description="설비 매뉴얼과 과거 점검 이력을 기반으로 AI가 답합니다."
      />

      <div className="grid h-[calc(100dvh-220px)] min-h-[440px] max-h-[679px] gap-5 lg:grid-cols-[285px_minmax(0,1fr)]">
        {/* 대화 목록 */}
        <aside className="flex h-full min-h-0 flex-col overflow-y-auto rounded-[14px] border border-fixops-border bg-white p-[14px]">
          <div className="flex items-center justify-between gap-2 px-2 py-3">
            <h2 className="text-[17px] font-bold">대화 목록</h2>

            <button
              type="button"
              onClick={startNewChat}
              disabled={sending}
              className="rounded-lg bg-fixops-primary px-3 py-2 text-xs font-semibold text-white hover:bg-fixops-primary-hover"
            >
              + 새 대화
            </button>
          </div>

          <div className="mt-3 flex flex-col gap-3">
            {conversations.map((conversation) => (
              <button
                key={conversation.id}
                type="button"
                disabled={sending}
                onClick={() => {
                  setActiveId(conversation.id);
                  setError("");
                }}
                aria-current={conversation.id === activeId ? "true" : undefined}
                className={`min-h-[66px] rounded-[9px] px-4 text-left text-[13px] font-semibold transition-colors ${
                  conversation.id === activeId
                    ? "bg-[#eaf1ff] text-fixops-text"
                    : "bg-fixops-background text-fixops-text hover:bg-[#eaf1ff]"
                }`}
              >
                <span className="block truncate">{conversation.title}</span>
              </button>
            ))}
          </div>

          <p className="mt-auto px-2 pt-6 text-xs leading-relaxed text-fixops-muted">
            현재 대화는 이 화면에서만 유지되며 새로고침하면 초기화됩니다.
          </p>
        </aside>

        {/* 채팅 패널 */}
        <div className="flex h-full min-h-0 min-w-0 flex-col overflow-hidden rounded-[14px] border border-fixops-border bg-white">
          <div className="border-b border-fixops-border px-7 py-6">
            <h2 className="text-[19px] font-bold">✧ FixOps AI Assistant</h2>
          </div>

          {/* 메시지 영역 */}
          <div
            ref={messagesRef}
            className="flex min-h-0 flex-1 flex-col gap-6 overflow-y-auto overscroll-contain px-6 py-6"
            role="log"
            aria-label="AI 채팅 메시지"
            aria-live="polite"
          >
            {messages.map((item) => (
              <div
                key={item.id}
                className={`max-w-[85%] whitespace-pre-wrap break-words rounded-[13px] px-5 py-5 text-[14px] leading-7 ${
                  item.role === "user"
                    ? "ml-auto bg-[#eaf1ff]"
                    : "mr-auto bg-fixops-background"
                }`}
              >
                {item.content}
              </div>
            ))}

            {sending && (
              <div className="mr-auto rounded-[13px] bg-fixops-background px-5 py-4 text-sm text-fixops-muted">
                AI가 응답을 생성하고 있습니다...
              </div>
            )}
          </div>

          {/* 추천 질문 */}
          <div className="px-6 pb-3">
            <p className="mb-2 text-xs text-fixops-muted">추천 질문</p>

            <div className="flex flex-wrap gap-2">
              {suggestions.map((suggestion) => (
                <button
                  key={suggestion}
                  type="button"
                  disabled={sending}
                  onClick={() => setMessage(suggestion)}
                  className="rounded-full border border-fixops-border bg-white px-3 py-2 text-xs text-fixops-muted hover:bg-[#eaf1ff]"
                >
                  {suggestion}
                </button>
              ))}
            </div>
          </div>

          {/* 오류 */}
          {error && (
            <div
              role="alert"
              className="mx-6 mb-3 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700"
            >
              {error}
            </div>
          )}

          {/* 입력창 */}
          <form
            onSubmit={handleSubmit}
            className="mx-6 mb-6 flex items-end gap-3 rounded-[12px] bg-fixops-background p-2"
          >
            <textarea
              value={message}
              onChange={(event) => setMessage(event.target.value)}
              onKeyDown={handleKeyDown}
              disabled={sending}
              maxLength={4000}
              rows={1}
              placeholder="무엇이든 질문해보세요..."
              aria-label="AI에게 질문"
              className="max-h-32 min-h-[38px] flex-1 resize-none border-0 bg-transparent px-3 py-2 text-[14px] text-fixops-text outline-none placeholder:text-fixops-muted"
            />

            <button
              type="submit"
              disabled={sending || !message.trim()}
              className="mb-1 rounded-full bg-fixops-primary px-5 py-2 text-xs font-bold text-white hover:bg-fixops-primary-hover"
            >
              {sending ? "전송 중" : "전송"}
            </button>
          </form>
        </div>
      </div>
    </section>
  );
}
