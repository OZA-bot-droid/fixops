from fastapi import APIRouter, Depends
from app.schemas.chat import ChatRequest, ChatResponse
from app.services.chat_service import run_chat
from app.schemas.auth import VerifiedUserContext
from app.api.dependencies.auth import require_verified_user
import logging

# prefix : 모든 라우트 앞에 붙을 경로
# tags : Swagger UI에서 표시할 그룹
router = APIRouter(prefix="/api/ai", tags=["AI chat"])

# 오류 기록용 Logger 생성
# __name__ : 현재 Python 모듈의 이름
# 따라서 어느 모듈에서 오류가 발생했는지 로그를 구분하는 데 도움이 됨
logger = logging.getLogger(__name__)


@router.post("/chat", response_model=ChatResponse)
async def chat(
    body: ChatRequest,
    # FastAPI는 chat()을 실행하기 전에 require_verified_user()를 호출
    # 해당 함수는 무조건 HTTP 401을 발생시키므로, 인증 없이 Agent가 실행되는 것을 막을 수 있음
    user: VerifiedUserContext = Depends(require_verified_user),
):
    answer = await run_chat(body.message, user)
    return ChatResponse(answer=answer)
