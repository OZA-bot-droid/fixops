from fastapi import FastAPI
from app.api.routes.chat import router

app = FastAPI(title="FixOps AI", version="0.1.0")

# include_router : 다른 모듈에 정의된 API를 애플리케이션에 등록
app.include_router(router)

@app.get('/health')
def health():
    return {'status': 'UP', 'service': 'ai-service'}
