import jwt
from app.core.config import get_settings


class JWTConfigurationError(Exception):
    """JWT verification configuration is missing."""


def verify_access_token(token: str) -> dict:
    settings = get_settings()

    if not all(
        [
            settings.jwt_public_key,
            settings.jwt_issuer,
            settings.jwt_audience,
        ]
    ):
        raise JWTConfigurationError("JWT verification configuration is incomplete")

    payload = jwt.decode(
        token,
        # RSA 공개키
        key=settings.jwt_public_key,
        # 허용할 서명 알고리즘 제한
        algorithms=["RS256"],
        # 발급자 검증
        issuer=settings.jwt_issuer,
        # 토큰 대상 검증
        audience=settings.jwt_audience,
        options={
            # 필수 Claim 존재 여부 검증
            "require": [
                "sub",
                "iss",  # 토큰 발급자 검증
                "aud",  # 토큰 대상 검증
                "exp",  # 만료 여부 검증
                "iat",  # 발급 시각 검증
                "org_id",
                "membership_id",
                "role",
            ],
        },
    )

    return payload
