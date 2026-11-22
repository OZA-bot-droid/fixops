import pytest
from pydantic import ValidationError
from app.schemas.auth import VerifiedUserContext


def test_verified_user_context_valid():
    context = VerifiedUserContext(
        member_id=10,
        organization_id=20,
    )

    # 가상의 테스트 데이터
    assert context.member_id == 10
    assert context.organization_id == 20


def test_verified_user_context_invalid_member_id():
    with pytest.raises(ValidationError):
        VerifiedUserContext(
            member_id=0,
            organization_id=20,
        )


def test_verified_user_context_invalid_organization_id():
    with pytest.raises(ValidationError):
        VerifiedUserContext(
            member_id=10,
            organization_id=-1,
        )
