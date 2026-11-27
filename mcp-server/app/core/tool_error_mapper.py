import httpx

from app.schemas.tool_result import (
    ToolFailure,
    build_tool_failure,
)


HTTP_ERROR_CODES: dict[int, tuple[str, bool]] = {
    400: ("INVALID_INPUT", False),
    401: ("UNAUTHENTICATED", False),
    403: ("ORG_ACCESS_DENIED", False),
    404: ("RESOURCE_NOT_FOUND", False),
    429: ("RATE_LIMITED", True),
}


def map_http_error(
    *,
    exc: httpx.HTTPError,
    tool_name: str,
    trace_id: str,
) -> ToolFailure:
    # HTTP 응답을 받았지만 4xx 또는 5xx인 경우
    if isinstance(exc, httpx.HTTPStatusError):
        status_code = exc.response.status_code

        if status_code in HTTP_ERROR_CODES:
            code, retryable = HTTP_ERROR_CODES[status_code]

        elif 500 <= status_code <= 599:
            code = "DEPENDENCY_UNAVAILABLE"
            retryable = True

        else:
            # 아직 합의되지 않은 비즈니스 오류를
            # 다른 오류로 잘못 변환하지 않음
            raise ValueError(f"Unsupported upstream HTTP status: {status_code}") from exc

    # 응답을 받기 전에 연결 장애 또는 타임아웃 발생
    elif isinstance(
        exc,
        (httpx.ConnectError, httpx.TimeoutException),
    ):
        code = "DEPENDENCY_UNAVAILABLE"
        retryable = True

    else:
        # 정의되지 않은 네트워크 오류도 임의로 변환하지 않음
        raise TypeError("Unsupported HTTP error type") from exc

    return build_tool_failure(
        tool_name=tool_name,
        code=code,
        retryable=retryable,
        trace_id=trace_id,
    )
