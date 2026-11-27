from functools import lru_cache
from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    asset_service_url: str = Field(default="http://localhost:8082")
    workorder_service_url: str = Field(default="http://localhost:8083")
    inventory_service_url: str = Field(default="http://localhost:8084")
    # gt(greater than) : 지정한 값보다 커야 함
    http_timeout: float = Field(default=5.0, gt=0)

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

@lru_cache
def get_settings() -> Settings:
    return Settings()