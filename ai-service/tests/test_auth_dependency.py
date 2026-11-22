from fastapi import Depends, FastAPI
from fastapi.testclient import TestClient
from app.api.dependencies.auth import require_verified_user
from app.schemas.auth import VerifiedUserContext

test_app = FastAPI()


@test_app.get("/protected")
def protected(
    # Depends(...) : FastAPI에게 의존성 함수 실행을 요청
    # require_verified_user : 실행할 함수 객체, () 괄호를 붙이지 않는 이유는 FastAPI가 요청을 처리하는 시점에 함수를 실행
    user: VerifiedUserContext = Depends(require_verified_user),
):
    return {"member_id": user.member_id}


def test_protected_without_auth():
    client = TestClient(test_app)

    response = client.get("/protected")

    assert response.status_code == 401


def fake_verified_user() -> VerifiedUserContext:
    return VerifiedUserContext(
        member_id=10,
        organization_id=20,
    )


def test_protected_with_override():

    # dependency_overrides[A] = B : FastAPI 애플리케이션이 제공하는 딕셔너리
    # A가 필요한 곳에서 test하는 동안 B를 대신 실행하도록 설정
    test_app.dependency_overrides[require_verified_user] = fake_verified_user

    try:
        client = TestClient(test_app)

        response = client.get("/protected")

        assert response.status_code == 200
        assert response.json()["member_id"] == 10

    finally:
        test_app.dependency_overrides.clear()
