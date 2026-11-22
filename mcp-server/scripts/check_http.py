# Python의 비동기 함수 실행 및 이벤트 루프 관리
import asyncio
import httpx

async def check_http_connection() -> None:
    async with httpx.AsyncClient(timeout=5.0) as client:
        try:
            response = await client.get("http://localhost:8085/health")

            # HTTP 4xx 또는 5xx이면 예외 발생
            response.raise_for_status()
            print(response.status_code)
            print(response.json())

            # status_code : HTTP 상태 코드
            # JSON 본문을 Python 객체로 변환
        except httpx.HTTPStatusError as e:
            print(f"HTTPError: {e.response.status_code}")
        except httpx.ConnectError as e:
            print(f"Connection failed: {e}")
        except httpx.TimeoutException as e:
            print(f"Request timed out: {e}")

if __name__ == "__main__":
    asyncio.run(check_http_connection())
