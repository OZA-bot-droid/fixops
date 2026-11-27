import httpx
from app.core.config import get_settings

# Java REST API 호출 공통 기능 담당
class FixOpsHTTPClient:
    # client를 의존성 주입(Dependency Injection) 받음
    def __init__(self, client: httpx.AsyncClient) -> None:
        self.client = client

    async def get(
            self,
            path: str,
            headers: dict[str, str] | None = None,
    ) -> dict:

        # 주입받은 AsyncClient로 GET 요청 전송
        response = await self.client.get(
            path,
            headers=headers,
        )

        # 4xx/5xx 응답일 때 예외 발생
        response.raise_for_status()

        # 정상 HTTP response를 Python 객체로 변환
        return response.json()


def create_http_client(
        # 호출할 서비스의 기본 주소
        base_url: str,

        # httpx.AsyncBaseTransport 비동기 HTTP 전송 구현의 기본 클래스
        transport: httpx.AsyncBaseTransport | None = None,
) -> httpx.AsyncClient:
    settings = get_settings()

    return httpx.AsyncClient(
        base_url=base_url,
        timeout=settings.http_timeout,
        transport=transport,
    )
