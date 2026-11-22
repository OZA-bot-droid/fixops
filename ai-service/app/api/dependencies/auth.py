from fastapi import HTTPException, status
from app.schemas.auth import VerifiedUserContext


# 임시 보안 경계
def require_verified_user() -> VerifiedUserContext:
    raise HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="인증 검증이 아직 구성되지 않았습니다.",
    )
