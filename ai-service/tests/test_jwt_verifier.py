import jwt
import pytest
from types import SimpleNamespace
from datetime import datetime, timedelta, UTC
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from app.core import jwt_verifier
from app.core.jwt_verifier import JWTConfigurationError, verify_access_token


@pytest.fixture
def jwt_test_context(monkeypatch):
    # 테스트용 RSA 개인키 생성
    private_key = rsa.generate_private_key(
        public_exponent=65537,
        key_size=2048,
    )

    # RSA 공개키를 PEM 문자열로 변환
    # public_key_pem = private_key.public_key().public_bytes(
    #     encoding=serialization.Encoding.PEM,
    #     format=serialization.PublicFormat.SubjectPublicKeyInfo,
    # ).decode("utf-8")
    public_key_pem = (
        private_key.public_key()
        .public_bytes(
            encoding=serialization.Encoding.PEM,
            format=serialization.PublicFormat.SubjectPublicKeyInfo,
        )
        .decode("utf-8")
    )

    # 실제 환경변수 대신 테스트 설정 사용
    settings = SimpleNamespace(
        jwt_public_key=public_key_pem,
        jwt_issuer="fixops-member-test",
        jwt_audience="fixops-ai-test",
    )

    monkeypatch.setattr(
        jwt_verifier,
        "get_settings",
        lambda: settings,
    )

    return private_key


def make_payload(
    expires_delta: timedelta = timedelta(minutes=15),
) -> dict:
    now = datetime.now(UTC)

    return {
        "sub": "10",
        "iss": "fixops-member-test",
        "aud": "fixops-ai-test",
        "iat": now,
        "exp": now + expires_delta,
        "org_id": 20,
        "membership_id": 30,
        "role": "USER",
    }


def test_valid_jwt(jwt_test_context):
    token = jwt.encode(
        make_payload(),
        jwt_test_context,
        algorithm="RS256",
    )

    payload = verify_access_token(token)

    assert payload["sub"] == "10"
    assert payload["org_id"] == 20
    assert payload["membership_id"] == 30
    assert payload["role"] == "USER"


def test_invalid_signature(jwt_test_context):
    # 검증에 등록되지 않은 다른 개인키
    other_private_key = rsa.generate_private_key(
        public_exponent=65537,
        key_size=2048,
    )

    token = jwt.encode(
        make_payload(),
        other_private_key,
        algorithm="RS256",
    )

    with pytest.raises(jwt.InvalidSignatureError):
        verify_access_token(token)


def test_expired_jwt(jwt_test_context):
    token = jwt.encode(
        make_payload(
            expires_delta=timedelta(minutes=-1),
        ),
        jwt_test_context,
        algorithm="RS256",
    )

    with pytest.raises(jwt.ExpiredSignatureError):
        verify_access_token(token)


def test_missing_required_claim(jwt_test_context):
    payload = make_payload()
    del payload["membership_id"]

    token = jwt.encode(
        payload,
        jwt_test_context,
        algorithm="RS256",
    )

    with pytest.raises(jwt.MissingRequiredClaimError):
        verify_access_token(token)


def test_wrong_audience(jwt_test_context):
    payload = make_payload()
    payload["aud"] = "another-service"

    token = jwt.encode(
        payload,
        jwt_test_context,
        algorithm="RS256",
    )

    with pytest.raises(jwt.InvalidAudienceError):
        verify_access_token(token)


def test_missing_jwt_configuration(monkeypatch):
    settings = SimpleNamespace(
        jwt_public_key=None,
        jwt_issuer=None,
        jwt_audience=None,
    )

    monkeypatch.setattr(
        jwt_verifier,
        "get_settings",
        lambda: settings,
    )

    with pytest.raises(JWTConfigurationError):
        verify_access_token("invalid-token")
