from datetime import datetime, UTC
from uuid import uuid4
from fastapi import Request
from fastapi.responses import JSONResponse
from app.core.exceptions import AIProviderUnavailableError, MCPConnectionError
from app.schemas.error import ErrorDetail, ErrorResponse


# 공통 함수 추출(Extract Function)
def build_error_response(
    code: str,
    message: str,
    status_code: int,
) -> JSONResponse:

    response = ErrorResponse(
        error=ErrorDetail(code=code, message=message),
        timestamp=datetime.now(UTC),
        traceId=str(uuid4()),
    )

    return JSONResponse(
        status_code=status_code,
        content=response.model_dump(
            mode="json",
            by_alias=True,
        ),
    )


async def ai_provider_exception_handler(
    request: Request, exc: AIProviderUnavailableError
) -> JSONResponse:

    return build_error_response(
        code="AI_PROVIDER_UNAVAILABLE",
        message="AI 서비스를 일시적으로 사용할 수 없습니다.",
        status_code=503,
    )


async def mcp_connection_exception_handler(
    request: Request, exc: MCPConnectionError
) -> JSONResponse:

    return build_error_response(
        code="MCP_CONNECTION_UNAVAILABLE",
        message="MCP 서비스를 일시적으로 사용할 수 없습니다.",
        status_code=503,
    )
