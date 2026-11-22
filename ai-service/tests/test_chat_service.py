import httpx  # Python에서 HTTP 요청을 보내기 위한 라이브러리
import pytest  # 테스트 코드를 실행하고 결과를 검증하는 프레임워크 - 일반적으로 test_로 시작하는 함수들을 찾아 실행

# unittest : Python의 기본 테스트 프레임워크
# mock : 실제 객체 대신 가짜 객체를 사용하는 기능을 제공
# AsyncMock : 비동기 함수의 동작을 대체하기 위한 클래스
from unittest.mock import AsyncMock

# APIConnectionError : OpenAI SDK 예외 클래스
from openai import APIConnectionError
from app.core.exceptions import AIProviderUnavailableError
from app.services.chat_service import run_chat
from app.schemas.auth import VerifiedUserContext


# decorator 형태의 pytest marker -> pytest-asyncio 플러그인이 비동기 테스트를 실행할 수 있도록 함
# run_chat()이 비동기 함수이기 때문에 사용
@pytest.mark.asyncio
async def test_run_chat_openai_connection_error(monkeypatch):

    # OpenAI 연결 오류에 필요한 요청 객체 생성
    # 실제 OpenAI 서버로 HTTP 요청을 보내지 않음
    request = httpx.Request(
        method="POST",
        # OpenAI의 Responses API 엔드포인트
        # 어떤 api 주소든 상관없는데 현재 test가 OpenAI API 연결 실패이기에 직관적으로 보여주기 위해 사용
        # httpx.Request()는 HTTP 요청을 표현하는 Python 객체만 생성
        url="https://api.openai.com/v1/responses",
    )

    # 가상의 OpenAI 연결 실패 예외 객체 생성 - message와 request 정보 전달
    error = APIConnectionError(
        message="Connection error",
        request=request,
    )

    # run_agent() 호출 시 예외 발생
    mock_run_agent = AsyncMock(side_effect=error)

    # 실제 Agent 호출을 Mock으로 대체
    monkeypatch.setattr(
        "app.services.chat_service.run_agent",
        mock_run_agent,
    )

    user = VerifiedUserContext(
        member_id=10,
        organization_id=20,
    )

    # 지정한 예외가 발생하는지 확인
    with pytest.raises(AIProviderUnavailableError):
        await run_chat("test", user)

    # 비동기 함수가 지정한 인자로 정확히 한 번 await 됐는지 확인
    # await될 때 전달된 인자가 "test"인지 확인
    mock_run_agent.assert_awaited_once_with("test", user)


@pytest.mark.asyncio
async def test_run_chat_passes_user_context(monkeypatch):
    user = VerifiedUserContext(
        member_id=10,
        organization_id=20,
    )

    mock_run_agent = AsyncMock(return_value="Test response")

    monkeypatch.setattr(
        "app.services.chat_service.run_agent",
        mock_run_agent,
    )

    result = await run_chat("hello", user)

    assert result == "Test response"
    mock_run_agent.assert_awaited_once_with("hello", user)
