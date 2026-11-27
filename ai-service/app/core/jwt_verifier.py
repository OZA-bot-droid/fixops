import jwt
from app.core.config import get_settings, PROJECT_ROOT
from pathlib import Path


class JWTConfigurationError(Exception):
    """JWT verification configuration is missing."""


def verify_access_token(token: str) -> dict:
    settings = get_settings()

    if not all(
        [
            settings.jwt_public_key_path,
            settings.jwt_issuer,
            settings.jwt_audience,
        ]
    ):
        raise JWTConfigurationError("JWT verification configuration is incomplete")

    try:
        public_key_path = Path(settings.jwt_public_key_path)

        if not public_key_path.is_absolute():
            public_key_path = PROJECT_ROOT / public_key_path

        public_key = public_key_path.read_bytes()
    except OSError as exc:
        raise JWTConfigurationError(
            "JWT public key file could not be loaded"
        ) from exc

    try:
        return jwt.decode(
            token,
            # RSA 공개키
            key=public_key,
            # 허용할 서명 알고리즘 제한
            algorithms=["RS256"],
            # 발급자 검증
            issuer=settings.jwt_issuer,
            # 토큰 대상 검증
            audience=settings.jwt_audience,
            options={
                # 필수 Claim 존재 여부 검증
                "require": [
                    "sub",  # member id
                    "iss",  # 토큰 발급자 검증
                    "aud",  # 토큰 대상 검증
                    "exp",  # 만료 여부 검증
                    "iat",  # 발급 시각 검증
                    "sid",  # redis에서 확인할 인증 세션의 UUID
                    "memberships",  # 활성 조직 소속 목록과 역할
                ],
            },
        )
    except jwt.InvalidKeyError as exc:
        raise JWTConfigurationError("JWT public key is invalid") from exc
