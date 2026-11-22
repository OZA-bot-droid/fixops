# AsyncMock : Python 표준 라이브러리에서 제공하는 비동기 함수용 가짜 객체
# async def로 정의한 비동기 함수를 테스트할 때, 실제 함수 대신 가짜 함수를 만들어 주는 역할을 함
from unittest.mock import AsyncMock
import pytest

# FastAPI 서버를 별도로 실행하지 않고 HTTP 요청을 테스트용 객체
# TestClient : FastAPI 테스트 클라이언트
from fastapi.testclient import TestClient
from app.core.exceptions import AIProviderUnavailableError, MCPConnectionError
from app.api.dependencies.auth import require_verified_user
from app.schemas.auth import VerifiedUserContext
from app.main import app


# monkeypatch : pytest에 기본적으로 등록되어 있는 Fixture
# Fixture : 테스트를 실행할 때 필요한 객체나 환경을 준비해서 테스트 함수에 제공하는 pytest의 기능
# pytest가 자동으로 monkeypatch 객체를 준비해서 전달
# monkeypatch의 주된 역할은 테스트 중에 객체의 속성이나 함수를 임시로 교체하는 것
def test_chat_llm_unavailable(monkeypatch, authenticated_client):

    # 비동기 함수 호출 시 지정한 예외 발생
    mock_run_chat = AsyncMock(
        # side_effect : Mock이 실행될 때 지정한 예외 또는 동작을 발생시키는 속성
        # 예외 객체를 지정하면 해당 Mock이 await될 때 그 예외가 발생
        side_effect=AIProviderUnavailableError("LLM unavailable")
    )

    # 테스트 동안 실제 함수 run_chat을 Mock으로 교체
    monkeypatch.setattr("app.api.routes.chat.run_chat", mock_run_chat)

    # FastAPI 테스트 클라이언트 생성
    # client = TestClient(app)

    # HTTP POST 요청 시뮬레이션
    # response = client.post(
    #     "api/ai/chat",
    #     json={
    #         "message": "test"
    #     }
    # )

    response = authenticated_client.post("api/ai/chat", json={"message": "test"})

    # assert 실제 결과가 예상 결과와 같은지 확인
    assert response.status_code == 503
    assert response.json()["success"] is False
    assert response.json()["error"]["code"] == "AI_PROVIDER_UNAVAILABLE"
    assert response.json()["error"]["message"] == (
        "AI 서비스를 일시적으로 사용할 수 없습니다."
    )
    assert "traceId" in response.json()
    assert "timestamp" in response.json()

    # 비동기 함수가 지정한 인자로 정확히 한 번 await 됐는지 확인
    # await될 때 전달된 인자가 "test"인지 확인
    mock_run_chat.assert_awaited_once_with(
        "test",
        VerifiedUserContext(
            member_id=10,
            organization_id=20,
        ),
    )


def test_chat_mcp_unavailable(monkeypatch, authenticated_client):
    mock_run_chat = AsyncMock(side_effect=MCPConnectionError("MCP unavailable"))

    monkeypatch.setattr("app.api.routes.chat.run_chat", mock_run_chat)

    # client = TestClient(app)
    #
    # response = client.post(
    #     "/api/ai/chat",
    #     json={
    #         "message": "test"
    #     }
    # )

    response = authenticated_client.post("/api/ai/chat", json={"message": "test"})

    assert response.status_code == 503
    assert response.json()["success"] is False
    assert response.json()["error"]["code"] == "MCP_CONNECTION_UNAVAILABLE"
    assert response.json()["error"]["message"] == (
        "MCP 서비스를 일시적으로 사용할 수 없습니다."
    )
    assert "traceId" in response.json()
    assert "timestamp" in response.json()

    mock_run_chat.assert_awaited_once_with(
        "test",
        VerifiedUserContext(
            member_id=10,
            organization_id=20,
        ),
    )


def test_chat_without_auth():
    client = TestClient(app)

    response = client.post(
        "/api/ai/chat",
        json={"message": "test"},
    )

    assert response.status_code == 401


def fake_verified_user() -> VerifiedUserContext:
    return VerifiedUserContext(
        member_id=10,
        organization_id=20,
    )


# @pytest.fixture : 테스트에 필요한 객체와 환경을 제공
@pytest.fixture
def authenticated_client():
    app.dependency_overrides[require_verified_user] = fake_verified_user

    try:
        with TestClient(app) as client:
            # 테스트 함수에 client 전달
            yield client
    finally:
        # 해당 인증 Override만 제거
        app.dependency_overrides.pop(require_verified_user, None)
