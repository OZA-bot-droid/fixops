from datetime import datetime, timezone
from uuid import uuid4
from fastapi import Request
from fastapi.responses import JSONResponse
from app.core.exceptions import AIProviderUnavailableError
from app.schemas.error import ErrorDetail, ErrorResponse

async def ai_provider_exception_handler(
    request: Request,
    exc: AIProviderUnavailableError
) -> JSONResponse:

    error = ErrorDetail(
        code="AI_PROVIDER_UNAVAILABLE",
        message="AI 서비스를 일시적으로 사용할 수 없습니다.",
    )

    response = ErrorResponse(
        error=error,
        timestamp=datetime.now(timezone.utc),
        traceId=str(uuid4()),
    )

    response.model_dump(
        mode="json",
        by_alias=True,
    )

    return JSONResponse(
        status_code=503,
        content=response,
    )