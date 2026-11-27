import jwt
from uuid import UUID
from fastapi import (
    Header,
    HTTPException,
    Security,
    status,
)
from fastapi.security import (
    HTTPAuthorizationCredentials,
    HTTPBearer,
)
from app.core.jwt_verifier import (
    JWTConfigurationError,
    verify_access_token,
)
from app.schemas.auth import VerifiedUserContext


bearer_scheme = HTTPBearer(auto_error=False)
ALLOWED_ROLES = {"ADMIN", "TECHNICIAN", "USER"}

def require_verified_user(
    # HTTP Bearer Authorization header에서 추출한 토큰
    credentials: HTTPAuthorizationCredentials | None = Security(
        bearer_scheme
    ),
    # frontend로부터 온 X-Organization-Id
    # 사용자가 직접 선택한 값이기 때문에 신뢰x
    organization_id: int | None = Header(
        default=None,
        alias="X-Organization-Id",
    ),
) -> VerifiedUserContext:

    # 1. Bearer Token 검사
    if credentials is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Authentication required",
        )

    # 2. JWT 서명 및 Claim 검증
    try:
        # 서명과 유효기간을 검증한 JWT Claim
        payload = verify_access_token(credentials.credentials)

    except jwt.InvalidTokenError as exc:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid access token",
        ) from exc

    except JWTConfigurationError as exc:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Authentication service unavailable",
        ) from exc


    # 3. 사용자 ID 검증
    try:
        member_id = int(payload["sub"])

        if isinstance(payload["sub"], bool) or member_id <= 0:
            raise ValueError("Invalid member ID")

        UUID(payload["sid"])

    except (KeyError, TypeError, ValueError) as exc:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid user identity",
        ) from exc


    # 4. 선택한 조직 검증
    if organization_id is None or organization_id <= 0:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Organization selection required",
        )


    # 5. JWT에 서명된 조직 소속 확인
    # 사용자에게 발급된 조직별 소속 목록
    memberships = payload.get("memberships")

    if not isinstance(memberships, list):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid memberships",
        )

    for membership in memberships:
        if not isinstance(membership, dict):
            continue

        org_id = membership.get("organizationId")
        membership_id = membership.get("membershipId")

        # 선택 조직에서의 ADMIN, TECHNICIAN, USER 권한
        role = membership.get("role")

        if (
            isinstance(org_id, bool)
            or not isinstance(org_id, int)
            or org_id != organization_id
        ):
            continue

        if(
            isinstance(membership_id, bool)
            or not isinstance(membership_id, int)
            or membership_id <=0
            or role not in ALLOWED_ROLES
        ):
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Organization access denied",
            )


        return VerifiedUserContext(
            member_id=member_id,
            organization_id=organization_id,
            role=role,
        )


    # 6. 선택한 조직의 소속이 없으면 거부
    raise HTTPException(
        status_code=status.HTTP_403_FORBIDDEN,
        detail="Organization access denied",
    )
