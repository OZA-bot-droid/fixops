from fastapi import APIRouter, HTTPException
from app.schemas.chat import ChatRequest, ChatResponse
from app.services.chat_service import run_chat
from app.core.exceptions import MCPConnectionError, AIProviderUnavailableError
import logging

# prefix : 모든 라우트 앞에 붙을 경로
# tags : Swagger UI에서 표시할 그룹
router = APIRouter(prefix="/api/ai", tags=["AI chat"])

# 오류 기록용 Logger 생성
# __name__ : 현재 Python 모듈의 이름이야. 따라서 어느 모듈에서 오류가 발생했는지 로그를 구분하는 데 도움이 됨
logger = logging.getLogger(__name__)

@router.post("/chat", response_model=ChatResponse)
async def chat(body: ChatRequest):
    try:
        answer = await run_chat(body.message)
        return ChatResponse(answer=answer)

    except MCPConnectionError as e:
        logger.exception("MCP server connection error")
        raise HTTPException(
            status_code=503,
            detail="AI server error"
        ) from e

    except AIProviderUnavailableError as e:
        logger.exception("LLM provider unavailable")
        raise HTTPException(
            status_code=503,
            detail="LLM provider unavailable or response error"
        ) from e
