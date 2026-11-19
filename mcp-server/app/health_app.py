"""MCP container readiness shell. /mcp must NOT be advertised until MCP SDK is integrated."""
from fastapi import FastAPI
app = FastAPI(title="FixOps MCP readiness", version="0.1.0")
@app.get("/health")
def health():
    return {"status": "UP", "service": "mcp", "mcpProtocolReady": False}
