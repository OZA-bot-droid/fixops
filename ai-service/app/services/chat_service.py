from app.agent.orchestrator import run_agent
from app.core.exceptions import MCPConnectionError
from openai import APIConnectionError, APITimeoutError, InternalServerError
from app.core.exceptions import AIProviderUnavailableError
from app.schemas.auth import VerifiedUserContext


async def run_chat(
    message: str,
    user: VerifiedUserContext,
) -> str:
    try:
        answer = await run_agent(message, user)
        return answer

    except RuntimeError as e:
        # str(e) : 발생한 예외의 메시지를 문자열로 변환
        # not in : 지정한 문자열이 포함되어 있지 않은지 확인
        if "Client failed to connect" not in str(e):
            # 현재 처리 중인 원래 예외를 그대로 다시 발생
            raise

        # 새 예외를 발생시키면서 원래 오류를 원인으로 연결
        raise MCPConnectionError("MCP server failed to connect") from e

    except (APIConnectionError, APITimeoutError, InternalServerError) as e:
        raise AIProviderUnavailableError("LLM provider unavailable") from e
