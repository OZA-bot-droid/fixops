from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict

# BaseSettings : 환경변수에서 값을 읽어 Python 객체로 만들어 주는 클래스
class Settings(BaseSettings):

    # BaseSettings는 기본적으로 필드명에 대응하는 대문자 환경변수를 읽을 수 있음
    # 대소문자 구분이 기본적으로 비활성화
    openai_api_key: str
    openai_model: str
    mcp_server_url: str

    # env_file : 읽어 올 .env 파일 경로
    # env_file_encoding : 파일의 문자 인코딩
    # extra : 정의하지 않은 추가 환경변수를 어떻게 처리할지 지정
    # extra="ignore" : .env 파일에 클래스에서 정의하지 않은 항목이 있더라도 무시하도록 하는 설정
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

settings = Settings()