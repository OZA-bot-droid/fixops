import httpx
import pytest
from app.clients.http_client import FixOpsHTTPClient, create_http_client

def mock_handler(request: httpx.Request) -> httpx.Response:
    return httpx.Response(
        status_code=200,
        json={
            "id": 100,
            "name": "Test Asset",
        },
    )

@pytest.mark.asyncio
async def test_common_http_client_get():
    transport = httpx.MockTransport(mock_handler)

    async with httpx.AsyncClient(
        transport=transport,
        base_url="http://localhost:8082",
    ) as client:
        http_client = FixOpsHTTPClient(client)

        result = await http_client.get("/api/assets/100")

        assert result["id"] == 100
        assert result["name"] == "Test Asset"

def mock_forbidden(request: httpx.Request) -> httpx.Response:
    return httpx.Response(
        status_code=403,
        json={
            "success": False,
            "error": {
                "code": "ORG_ACCESS_DENIED",
                "message": "Access Denied",
            },
        },
    )

@pytest.mark.asyncio
async def test_common_http_client_forbidden():
    transport = httpx.MockTransport(mock_forbidden)

    async with httpx.AsyncClient(
        transport=transport,
        base_url="http://localhost:8082",
    ) as client:
        http_client = FixOpsHTTPClient(client)

        with pytest.raises(httpx.HTTPStatusError) as exc_info:
            await http_client.get("/api/assets/100")

        assert exc_info.value.response.status_code == 403
        assert exc_info.value.response.json()["error"]["code"] == "ORG_ACCESS_DENIED"

def mock_unavailable(request: httpx.Request) -> httpx.Response:
    return httpx.Response(
        status_code=503,
        json={
            "success": False,
            "error": {
                "code": "DEPENDENCY_UNAVAILABLE",
                "message": "Dependency Unavailable",
            },
        },
    )

@pytest.mark.asyncio
async def test_common_http_client_unavailable():
    transport = httpx.MockTransport(mock_unavailable)

    async with httpx.AsyncClient(
        transport=transport,
        base_url="http://localhost:8082",
    ) as client:
        http_client = FixOpsHTTPClient(client)

        with pytest.raises(httpx.HTTPStatusError) as exc_info:
            await http_client.get("/api/assets/100")

        assert exc_info.value.response.status_code == 503
        assert exc_info.value.response.json()["error"]["code"] == "DEPENDENCY_UNAVAILABLE"

def mock_connection_error(request: httpx.Request) -> httpx.Response:
    raise httpx.ConnectError(
        "Connection refused",
        request=request,
    )

@pytest.mark.asyncio
async def test_common_http_client_connection_error():
    transport = httpx.MockTransport(mock_connection_error)

    async with httpx.AsyncClient(
        transport=transport,
        base_url="http://localhost:8082",
    ) as client:
        http_client = FixOpsHTTPClient(client)

        with pytest.raises(httpx.ConnectError) as exc_info:
            await http_client.get("/api/assets/100")

    assert "Connection refused" in str(exc_info.value)

def mock_read_timeout(request: httpx.Request) -> httpx.Response:
    raise httpx.ReadTimeout(
        "Read Timed out",
        request=request,
    )

@pytest.mark.asyncio
async def test_common_http_client_read_timeout():
    transport = httpx.MockTransport(mock_read_timeout)

    async with httpx.AsyncClient(
        transport=transport,
        base_url="http://localhost:8082",
        timeout=5.0,
    ) as client:
        http_client = FixOpsHTTPClient(client)

        with pytest.raises(httpx.TimeoutException) as exc_info:
            await http_client.get("/api/assets/100")

    assert isinstance(exc_info.value, httpx.ReadTimeout)

@pytest.mark.asyncio
async def test_create_http_client():
    def mock_handler(request: httpx.Request) -> httpx.Response:
        assert str(request.url) == "http://localhost:8082/health"

        return httpx.Response(
            status_code=200,
            json={"status": "UP"},
        )

    transport = httpx.MockTransport(mock_handler)

    async with create_http_client(
        base_url="http://localhost:8082",
        transport=transport,
    ) as client:
        http_client = FixOpsHTTPClient(client)

        result = await http_client.get("/health")

    assert result["status"] == "UP"

def mock_unauthenticated(request: httpx.Request) -> httpx.Response:
    return httpx.Response(
        status_code=401,
        json={
            "success": False,
            "error": {
                "code": "UNAUTHENTICATED",
                "message": "인증이 필요합니다.",
            },
        },
    )

@pytest.mark.asyncio
async def test_common_http_client_unauthenticated():
    transport = httpx.MockTransport(mock_unauthenticated)

    async with httpx.AsyncClient(
        transport=transport,
        base_url="http://localhost:8082",
    ) as client:
        http_client = FixOpsHTTPClient(client)

        with pytest.raises(httpx.HTTPStatusError) as exc_info:
            await http_client.get("/api/assets/100")

    assert exc_info.value.response.status_code == 401
    assert exc_info.value.response.json()["error"]["code"] == "UNAUTHENTICATED"

def mock_not_found(request: httpx.Request) -> httpx.Response:
    return httpx.Response(
        status_code=404,
        json={
            "success": False,
            "error": {
                "code": "RESOURCE_NOT_FOUND",
                "message": "존재하지 않습니다.",
            },
        },
    )

@pytest.mark.asyncio
async def test_common_http_client_not_found():
    transport = httpx.MockTransport(mock_not_found)

    async with httpx.AsyncClient(
        transport=transport,
        base_url="http://localhost:8082",
    ) as client:
        http_client = FixOpsHTTPClient(client)

        with pytest.raises(httpx.HTTPStatusError) as exc_info:
            await http_client.get("/api/assets/999")

    assert exc_info.value.response.status_code == 404
    assert exc_info.value.response.json()["error"]["code"] == "RESOURCE_NOT_FOUND"

def mock_authorized_request(request: httpx.Request) -> httpx.Response:
    assert request.headers["Authorization"] == "Bearer test-token"

    return httpx.Response(
        status_code=200,
        json={"id": 100}
    )

@pytest.mark.asyncio
async def test_common_http_client_headers():
    transport = httpx.MockTransport(mock_authorized_request)

    async with httpx.AsyncClient(
        transport=transport,
        base_url="http://localhost:8082",
    ) as client:
        http_client = FixOpsHTTPClient(client)

        result = await http_client.get(
            "/api/assets/100",
            headers={
                "Authorization": "Bearer test-token",
            },
        )

    assert result["id"] == 100

@pytest.mark.asyncio
async def test_request_headers_are_isolated():
    received_headers = []
    def mock_headers_are_isolated(request: httpx.Request) -> httpx.Response:
        received_headers.append(
            # header가 없으면 None을 반환
            # request.headers["Authorization"]는 해당 header가 없으면 KeyError 발생
            request.headers.get("Authorization")
        )

        return httpx.Response(
            status_code=200,
            json={"success": True}
        )

    transport = httpx.MockTransport(mock_headers_are_isolated)

    async with httpx.AsyncClient(
        transport=transport,
        base_url="http://localhost:8082",
    ) as client:
        http_client = FixOpsHTTPClient(client)

        await http_client.get(
            "/api/assets/100",
            headers={
                "Authorization": "Bearer user-A-test-token",
            },
        )

        await http_client.get(
            "/api/assets/100",
            headers={
                "Authorization": "Bearer user-B-test-token",
            },
        )

        await http_client.get("/health")

    # 위험한 방식 — 공유 Client의 기본 인증 헤더를 요청마다 변경
    # 여러 사용자가 같은 Client를 공유하면 다른 요청의 인증 정보에 영향을 줄 수 있음

    # 권장 방식 — 요청별 헤더 전달
    # 전달한 헤더가 해당 요청에만 적용되도록 구성할 수 있음
    # 실제 구현에서는 이 토큰을 임의의 문자열이나 클라이언트가 제출한 역할 정보로 구성하지 않아야 해. 인증된 요청 컨텍스트에서 안전하게 생성·전달해야 해.
    assert received_headers == [
        "Bearer user-A-test-token",
        "Bearer user-B-test-token",
        None,
    ]