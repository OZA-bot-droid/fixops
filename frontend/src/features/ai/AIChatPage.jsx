import { useEffect, useRef, useState } from "react";
import { PageHeader } from "../../components/layout/AppLayout";
import RagSources from "./RagSources";
import {
  createConversation,
  fetchConversations,
  fetchConversationDetail,
  sendConversationMessage,
  deleteConversation
} from "./conversationApi"
import { Button } from "../../components/ui/index";
import { useOrganization } from "../member/OrganizationProvider"
import DocumentManagementModal from "./DocumentManagementModal";
import { explainError } from "../../api/errors";

function getAIChatErrorMessage(error) {
  const code = error?.response?.data?.error?.code;

  if (code === "AI_PROVIDER_UNAVAILABLE") {
    return "AI 응답 서비스를 일시적으로 사용할 수 없습니다.";
  }

  if (code === "DATABASE_POOL_UNAVAILABLE") {
    return "데이터베이스 연결이 일시적으로 부족합니다.";
  }

  if (code === "MCP_CONNECTION_UNAVAILABLE") {
    return "업무 정보 조회 서비스를 일시적으로 사용할 수 없습니다.";
  }

  return explainError(error);
}

export default function AIChatPage() {

  const {
    activeOrganizationId,   // Header에서 선택한 조직 ID
    activeComplexId,        // 선택한 단지 ID. 없으면 null
    activeOrganizationKind, // FACTORY 또는 BUILDING
    activeRole,             // 현재 조직에서의 사용자 역할
  } = useOrganization();

  const organizationId = activeOrganizationId;
  const complexId = activeComplexId;

  const [documentModalOpen, setDocumentModalOpen] = useState(false);
  const [conversations, setConversations] = useState([]);
  const [activeId, setActiveId] = useState(null);
  const [messages, setMessages] = useState([]);
  const [loadingHistory, setLoadingHistory] = useState(true);
  const [loadingMessages, setLoadingMessages] = useState(false);
  const [message, setMessage] = useState("");
  const [sending, setSending] = useState(false);
  const [error, setError] = useState("");
  const [managingConversation, setManagingConversation] = useState(false);  // 생성 및 삭제 중 화면 버튼 상태에 반영

  const messagesRef = useRef(null);
  const textareaRef = useRef(null);
  const sendingRef = useRef(false); // 중복 요청 방지
  const managingRef = useRef(false);  // 새 대화 생성 및 삭제의 중복 실행을 즉시 차단
  const skipInitialLoadRef = useRef(null);  // 첫 메시지 전송으로 방금 생성된 대화방은 기존 메시지 조회를 생략해 임시 메시지가 사라지지 않도록 한다.

  const activeConversation = conversations.find(
    (conversation) => conversation.id === activeId
  );

  useEffect(() => {
    const container = messagesRef.current;

    if (!container) return;

    container.scrollTop = container.scrollHeight;
  }, [messages.length, sending, activeId]);

  useEffect(() => {
    let cancelled = false;

    async function loadHistory() {
      setLoadingHistory(true);
      setConversations([]);
      setActiveId(null);
      setMessages([]);

      try {
        const data = await fetchConversations(
          Number(organizationId)
        );

        if (cancelled) return;

        setConversations(data);
        setActiveId(data[0]?.id ?? null);
      } catch (err) {
        if (!cancelled) {
          setError(explainError(err));
        }
      } finally {
        if (!cancelled) setLoadingHistory(false);
      }
    }

    if (Number(organizationId) > 0) {
      loadHistory();
    } else {
      setConversations([]);
      setActiveId(null);
      setMessages([]);
      setLoadingHistory(false);
    }

    return () => {
      cancelled = true;
    };
  }, [organizationId]);

  useEffect(() => {
    if(!activeId) {
      setMessages([]);
      setLoadingMessages(false);
      return;
    }

    // 첫 메시지를 보내기 위해 방금 생성한 대화방이면
    // 서버 조회로 임시 사용자 메시지를 덮어쓰지 않는다.
    if (skipInitialLoadRef.current === activeId) {
      skipInitialLoadRef.current = null;
      setLoadingMessages(false);
      return;
    }

    // API 요청 취소 및 오래된 응답 방지
    // 오래된 응답이 React 상태를 변경하는 것을 방지하는 데 도움
    const controller = new AbortController();
    async function loadMessages() {
      setLoadingMessages(true);
      setMessages([])

      try {
        const detail = await fetchConversationDetail({
          organizationId: Number(organizationId),
          conversationId: activeId,
          signal: controller.signal,
        });

        if (!controller.signal.aborted) {
          setMessages(detail.messages ?? []);
        }
      } catch (err) {
        if (!controller.signal.aborted) {
          setError(getAIChatErrorMessage(err));
        }
      } finally {
        if (!controller.signal.aborted) setLoadingMessages(false);
      }
    }

    loadMessages();
    return () => controller.abort();
  }, [activeId, organizationId]);

  useEffect(() => {
    const textarea = textareaRef.current;
    if (!textarea) return;

    textarea.style.height = "auto";

    const nextHeight = Math.min(
      textarea.scrollHeight,
      180,
    );

    textarea.style.height = `${nextHeight}px`;
    textarea.style.overflowY =
      textarea.scrollHeight > 180 ? "auto" : "hidden";
  }, [message]);

  async function startNewChat() {
    if (
      sending ||
      loadingHistory ||
      loadingMessages ||
      managingConversation ||
      sendingRef.current ||
      managingRef.current
    ) return;

    const orgId = organizationId == null ? null : Number(organizationId)

    if (!Number.isSafeInteger(orgId) || orgId <= 0) {
      setError("유효한 조직 ID가 필요합니다.");
      return;
    }

    // 아직 DB에 저장된 대화방을 생성하지 않는다.
    // activeId === null 은 신규 대화 작성 상태를 뜻한다.
    setActiveId(null);
    setMessages([]);
    setMessage("");
    setError("");

    textareaRef.current?.focus();
  }

  async function handleDeleteConversation(conversationId) {
    if (
      sending ||
      loadingHistory ||
      loadingMessages ||
      sendingRef.current ||
      managingRef.current
    ) return;

    managingRef.current = true;
    setManagingConversation(true);
    setError("");

    try {
      await deleteConversation({
        organizationId: Number(organizationId),
        conversationId,
      });

      const remaining = conversations.filter(
        (conversation) => conversation.id !== conversationId
      );

      setConversations(remaining);

      if (activeId === conversationId) {
        setActiveId(remaining[0]?.id ?? null);
        setMessages([]);
      }
    } catch (err) {
      setError(getAIChatErrorMessage(err));
    } finally {
      managingRef.current = false;
      setManagingConversation(false);
    }
  }

  async function handleSubmit(event) {
    event.preventDefault();

    const text = message.trim();

    if (
      !text ||
      sendingRef.current ||
      managingRef.current ||
      sending ||
      managingConversation ||
      loadingHistory ||
      loadingMessages
    ) return;

    const orgId = Number(organizationId);

    if (!Number.isSafeInteger(orgId) || orgId <= 0) {
      setError("유효한 조직 ID가 필요합니다.");
      return;
    }

    // 기존 대화방을 선택한 상태인데
    // 대화 목록에서 찾을 수 없는 경우 전송하지 않는다.
    if (activeId && !activeConversation) {
      setError("대화방을 확인할 수 없습니다.");
      return;
    }

    // 신규 대화일 때만 단지 범위를 검사한다.
    let selectedComplexId = null;

    if (!activeId) {
      selectedComplexId =
        complexId == null ? null : Number(complexId);

      if (
        selectedComplexId !== null &&
        (
          !Number.isSafeInteger(selectedComplexId) ||
          selectedComplexId <= 0
        )
      ) {
        setError("유효한 단지 ID가 필요합니다.");
        return;
      }

      if (
        activeOrganizationKind === "BUILDING" &&
        selectedComplexId === null
      ) {
        setError("단지를 먼저 선택해 주세요.");
        return;
      }
    }

    // 서버 응답 전 사용자 메시지를 화면에 표시
    const temporaryId = `pending-${crypto.randomUUID()}`;

    // 서버가 응답하기 전에 화면에 먼저 표시할 메시지
    const pendingMessage = {
      id: temporaryId,
      role: "user",
      content: text,
      sources: [],
      pending: true,
    };

    sendingRef.current = true;
    setSending(true);
    setError("");
    setMessage("");

    // 사용자 질문 즉시 표시
    setMessages((previous) => [
      ...previous,
      pendingMessage,
    ]);

    // 기존 대화면 기존 ID 사용,
    // 새 대화면 아래에서 생성된 ID로 변경
    let conversationId = activeId;

    try {
      // 1. 신규 대화일 때만 채팅방 생성
      if (!conversationId) {
        const created = await createConversation({
          organizationId: orgId,
          complexId: selectedComplexId,
        });

        conversationId = created.id;

        // 채팅방 생성 후의 최초 조회가
        // 임시 메시지를 덮어쓰지 않도록 처리
        skipInitialLoadRef.current = conversationId;

        setConversations((previous) => [
          created,
          ...previous,
        ]);

        setActiveId(conversationId);
      }

      // 2. 기존 또는 새로 생성된 채팅방에 전송
      const result = await sendConversationMessage({
        organizationId: orgId,
        conversationId,
        message: text,
      });

      // 3. 임시 메시지를 실제 서버 메시지로 교체
      setMessages((previous) => {
        const updated = previous.map((item) =>
          item.id === temporaryId
            ? result.user_message
            : item
        );

        return [
          ...updated,
          result.assistant_message,
        ];
      });

      // 4. 기존 서버의 자동 제목 정책과 목록 순서 유지
      // 사용자 지정 제목 기능 구현 시 자동 변경 제거 예정
      setConversations((previous) => {
        const updated = previous.map((conversation) => {
          if (conversation.id !== conversationId) {
            return conversation;
          }

          return {
            ...conversation,
            title:
              conversation.title === "새 대화"
                ? text.slice(0, 200)
                : conversation.title,
            updated_at: new Date().toISOString(),
          };
        });

        return updated.sort(
          (a, b) =>
            new Date(b.updated_at) -
            new Date(a.updated_at)
        );
      });

    } catch (requestError) {
    // 채팅방 생성 이후 메시지 전송이 실패했다면
    // 이미 생성된 conversationId를 유지한다.
    // 다음 전송에서 다시 채팅방을 생성하지 않는다.
      setMessages((previous) =>
        previous.map((item) =>
          item.id === temporaryId
            ? {
                ...item,
                pending: false,
                failed: true,
              }
            : item
        )
      );

      setError(getAIChatErrorMessage(requestError));
    } finally {
      sendingRef.current = false;
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

      <div className="grid h-[calc(100dvh-210px)] min-h-[560px] gap-5 lg:grid-cols-[285px_minmax(0,1fr)]">
        {/* 대화 목록 */}
        <aside className="flex h-full min-h-0 flex-col overflow-y-auto rounded-[14px] border border-fixops-border bg-white p-[14px]">
          <div className="flex items-center justify-between gap-2 px-2 py-3">
            <h2 className="text-[17px] font-bold">대화 목록</h2>

            <button
              type="button"
              onClick={startNewChat}
              disabled={
                sending ||
                managingConversation ||
                loadingHistory ||
                loadingMessages
              }
              className="rounded-lg bg-fixops-primary px-3 py-2 text-xs font-semibold text-white hover:bg-fixops-primary-hover"
            >
              + 새 대화
            </button>
          </div>

          <div className="mt-3 flex flex-col gap-3">
            {loadingHistory && (
              <p className="text-sm text-fixops-muted">
                대화 목록을 불러오는 중...
              </p>
            )}

            {!loadingHistory && conversations.length === 0 && (
              <p className="text-sm text-fixops-muted">
                저장된 대화가 없습니다. 첫 질문을 입력하면 대화가 시작됩니다.
              </p>
            )}

            {!loadingHistory &&
              conversations.map((conversation) => (
                <div
                  key={conversation.id}
                  className={`flex items-center gap-2 rounded-lg p-2 ${
                    conversation.id === activeId
                      ? "bg-[#eaf1ff]"
                      : "bg-fixops-background"
                  }`}
                >
                  <button
                    type="button"
                    disabled={
                      sending ||
                      loadingMessages ||
                      managingConversation
                    }
                    onClick={() => {
                      setActiveId(conversation.id);
                      setError("");
                    }}
                    className="min-w-0 flex-1 truncate text-left text-sm"
                  >
                    {conversation.title}
                  </button>

                  <button
                    type="button"
                    disabled={
                      sending ||
                      managingConversation ||
                      loadingHistory ||
                      loadingMessages
                    }
                    onClick={() =>
                      handleDeleteConversation(conversation.id)
                    }
                    className="rounded px-2 py-1 text-xs text-red-600 hover:bg-red-50"
                    aria-label={`${conversation.title} 삭제`}
                  >
                    삭제
                  </button>
                </div>
              ))}
          </div>

          <p className="mt-auto px-2 pt-6 text-xs leading-relaxed text-fixops-muted">
            대화 내역은 서버에 저장되며 다시 로그인해도 유지됩니다.
          </p>
        </aside>

        {/* 채팅 패널 */}
        <div className="flex h-full min-h-0 min-w-0 flex-col overflow-hidden rounded-[14px] border border-fixops-border bg-white">
          <div className="flex items-center justify-between gap-3 border-b border-fixops-border px-7 py-5">
            <h2 className="text-[19px] font-bold">✧ FixOps AI Assistant</h2>

            <Button
              variant="secondary"
              onClick={() => setDocumentModalOpen(true)}
              disabled={!organizationId}
            >
              문서 관리
            </Button>
          </div>

          {/* 메시지 영역 */}
          <div
            ref={messagesRef}
            className="flex min-h-0 flex-1 flex-col gap-6 overflow-y-auto overscroll-contain px-6 py-6"
            role="log"
            aria-label="AI 채팅 메시지"
            aria-live="polite"
          >
            {loadingMessages && (
              <p className="text-sm text-fixops-muted">
                이전 대화를 불러오는 중...
              </p>
            )}

            {messages.map((item) => (
              <div
                key={item.id}
                className={`max-w-[85%] whitespace-pre-wrap break-words rounded-[13px] px-5 py-5 text-[14px] leading-7 ${
                  item.role === "user"
                    ? "ml-auto bg-[#eaf1ff]"
                    : "mr-auto bg-fixops-background"
                }`}
              >
                <div>{item.content}</div>

                {item.failed && (
                  <div className="mt-2 flex items-center gap-2">
                    <span className="text-xs text-red-600">
                      전송 실패
                    </span>

                    <button
                      type="button"
                      disabled={sending}
                      onClick={() => {
                        setMessage(item.content);

                        setMessages((previous) =>
                          previous.filter(
                            (entry) => entry.id !== item.id
                          )
                        );

                        textareaRef.current?.focus();
                      }}
                      className="text-xs font-semibold text-fixops-primary underline"
                    >
                      다시 작성
                    </button>
                  </div>
                )}

                {item.role === "assistant" && (
                  <RagSources sources={item.sources} />
                )}
              </div>
            ))}

            {sending && (
              <div className="mr-auto rounded-[13px] bg-fixops-background px-5 py-4 text-sm text-fixops-muted">
                AI가 응답을 생성하고 있습니다...
              </div>
            )}
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
            className="mx-4 mb-4 flex shrink-0 items-end gap-2 rounded-[22px] border border-fixops-border bg-fixops-background p-2 shadow-sm sm:mx-6 sm:mb-6"
          >
            <textarea
              ref={textareaRef}
              value={message}
              onChange={(event) => setMessage(event.target.value)}
              onKeyDown={handleKeyDown}
              disabled={sending}
              maxLength={4000}
              rows={1}
              placeholder={sending? "전송 중..." : "무엇이든 질문해보세요..."}
              aria-label="AI에게 질문"
              aria-busy={sending}
              className="min-h-[42px] min-w-0 flex-1 resize-none border-0 bg-transparent px-3 py-2.5 text-[14px] leading-6 text-fixops-text outline-none placeholder:text-fixops-muted"
            />

            <button
              type="submit"
              disabled={
                sending ||
                managingConversation ||
                loadingHistory ||
                loadingMessages ||
                !message.trim()
              }
              className="mb-1 shrink-0 rounded-full bg-fixops-primary px-5 py-2 text-xs font-bold text-white hover:bg-fixops-primary-hover"
            >
              {sending ? "전송 중" : "전송"}
            </button>
          </form>
        </div>

        <DocumentManagementModal
          open={documentModalOpen}
          onClose={() => setDocumentModalOpen(false)}
          organizationId={organizationId}
          complexId={complexId}
          organizationKind={activeOrganizationKind}
          role={activeRole}
        />
      </div>
    </section>
  );
}
