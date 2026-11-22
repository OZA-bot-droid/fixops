from fastapi import FastAPI
from app.api.routes.chat import router
from app.core.exception_handlers import (
    ai_provider_exception_handler,
    mcp_connection_exception_handler,
)
from app.core.exceptions import AIProviderUnavailableError, MCPConnectionError

app = FastAPI(title="FixOps AI", version="0.1.0")

# include_router : 다른 모듈에 정의된 API를 애플리케이션에 등록
app.include_router(router)
app.add_exception_handler(AIProviderUnavailableError, ai_provider_exception_handler)
app.add_exception_handler(MCPConnectionError, mcp_connection_exception_handler)


@app.get("/health")
def health():
    return {"status": "UP", "service": "ai-service"}
