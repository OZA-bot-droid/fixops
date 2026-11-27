# BaseModel : 데이터를 검증하는 Pydantic 클래스
# Field : 각 필드에 검증 조건을 지정하는 함수
from pydantic import BaseModel, Field
from typing import Literal


class VerifiedUserContext(BaseModel):
    # 인증된 사용자 식별자
    # gt(Greater Than) : 지정한 값보다 커야 한다는 조건
    member_id: int = Field(gt=0)
    # 검증된 현재 조직 식별자
    organization_id: int = Field(gt=0)
    role: Literal[
        "ADMIN",
        "TECHNICIAN",
        "USER",
    ] | None = None
