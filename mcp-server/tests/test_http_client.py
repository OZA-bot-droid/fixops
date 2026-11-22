import httpx
import pytest

def mock_handler(request: httpx.Request) -> httpx.Response:
    return httpx.Response(
        status_code=200,
        json={"status": "UP", "service": "asset-service"}
    )

# pytest가 비동기 테스트 함수를 실행하도록 표시하는 데코레이터
@pytest.mark.asyncio
async def test_http_success():
    # 나중에 요청이 발생했을 때 실행할 함수 자체를 전달하는 것
    transport = httpx.MockTransport(mock_handler)

    async with httpx.AsyncClient(
        # HTTP 요청을 처리할 전송 객체
        transport=transport,
        # 상대 경로 요청에 사용할 기본 URL
        base_url="http://localhost:8082",
    ) as client:
        response = await client.get("/health")
        response.raise_for_status()

        assert response.status_code == 200
        assert response.json()["status"] == "UP"
        assert response.json()["service"] == "asset-service"

# HTTP 403 Mock 함수
def mock_forbidden_handler(request: httpx.Request) -> httpx.Response:
    return httpx.Response(
        status_code=403,
        json={
            "success": False,
            "error": {
                "code": "ORG_ACCESS_DENIED",
                "message": "Access Denied",
            }
        }
    )

# HTTP 403 test 함수
@pytest.mark.asyncio
async def test_http_forbidden():
    transport = httpx.MockTransport(mock_forbidden_handler)

    async with httpx.AsyncClient(
        transport=transport,
        base_url="http://localhost:8082",
    ) as client:
        response = await client.get("/api/assets/100")

        # pytest.raises() : 특정 예외가 발생하는지 확인하는 기능
        # exc_info : 발생한 예외 정보가 대입
        with pytest.raises(httpx.HTTPStatusError) as exc_info:
            response.raise_for_status()

        #exc_info.value : 실제 발생한 예외 객체
        #exc_info.value.response : 예외를 발생시킨 HTTP 응답
        assert exc_info.value.response.status_code == 403
        assert exc_info.value.response.json()["error"]["code"] == "ORG_ACCESS_DENIED"

# HTTP 503 Mock 함수
def mock_unavailable_handler(request: httpx.Request) -> httpx.Response:
    return httpx.Response(
        status_code=503,
        json={
            "success": False,
            "error": {
                "code": "DEPENDENCY_UNAVAILABLE",
                "message": "서비스를 일시적으로 사용할 수 없습니다.",
            }
        }
    )

# HTTP 503 test 함수
@pytest.mark.asyncio
async def test_http_unavailable():
    transport = httpx.MockTransport(mock_unavailable_handler)

    async with httpx.AsyncClient(
        transport=transport,
        base_url="http://localhost:8082",
    ) as client:
        response = await client.get("/api/assets/100")

        with pytest.raises(httpx.HTTPStatusError) as exc_info:
            response.raise_for_status()

        assert exc_info.value.response.status_code == 503
        assert exc_info.value.response.json()["error"]["code"] == "DEPENDENCY_UNAVAILABLE"

# ConnectionError Mock 함수
def mock_connection_error(request: httpx.Request) -> httpx.Response:
    raise httpx.ConnectError(
        "Connection refused",
        request=request
    )

# ConnectionError Mock test 함수
@pytest.mark.asyncio
async def test_http_connection_error():
    transport = httpx.MockTransport(mock_connection_error)

    async with httpx.AsyncClient(
        transport=transport,
        base_url="http://localhost:8082",
    ) as client:
        with pytest.raises(httpx.ConnectError) as exc_info:
            await client.get("/health")

    assert "Connection refused" in str(exc_info.value)

# TimeoutException Mock 함수
def mock_timeout_error(
        request: httpx.Request,
) -> httpx.Response:
    raise httpx.ReadTimeout(
        "Read timed out",
        request=request,
    )

# TimeoutException Mock test 함수
@pytest.mark.asyncio
async def test_http_timeout():
    transport = httpx.MockTransport(mock_timeout_error)

    async with httpx.AsyncClient(
        transport=transport,
        base_url="http://localhost:8082",
        timeout=5.0,
    ) as client:
        with pytest.raises(httpx.TimeoutException) as exc_info:
            await client.get("/health")

    # isinstance() : 객체가 특정 클래스 또는 해당 클래스의 하위 클래스에 속하는지 확인하는 Python 내장 함수
    assert isinstance(exc_info.value, httpx.ReadTimeout)
