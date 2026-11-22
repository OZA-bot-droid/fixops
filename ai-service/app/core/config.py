"""AI runtime configuration.

Settings are loaded when get_settings() is called, not during import of this
module. This keeps schema-only tooling independent of live LLM credentials.
"""

from functools import lru_cache
from pydantic_settings import BaseSettings, SettingsConfigDict


# BaseSettings : 환경변수에서 값을 읽어 Python 객체로 만들어 주는 클래스
class Settings(BaseSettings):
    # OpenAI
    # BaseSettings는 기본적으로 필드명에 대응하는 대문자 환경변수를 읽을 수 있음
    # 대소문자 구분이 기본적으로 비활성화
    openai_api_key: str
    openai_model: str

    # MCP
    mcp_server_url: str

    # JWT
    # Member Service가 발급한 JWT의 RSA 서명 검증
    jwt_public_key: str
    # 허용할 JWT 발급자
    jwt_issuer: str
    # AI Service가 수신할 수 있는 토큰인지 검증
    jwt_audience: str

    # env_file : 읽어 올 .env 파일 경로
    # env_file_encoding : 파일의 문자 인코딩
    # extra : 정의하지 않은 추가 환경변수를 어떻게 처리할지 지정
    # extra="ignore" : .env 파일에 클래스에서 정의하지 않은 항목이 있더라도 무시하도록 하는 설정
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )


@lru_cache
def get_settings() -> Settings:
    return Settings()
