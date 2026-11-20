"""MCP server placeholder only.

Do not expose a fake MCP endpoint: select and lock the compatible Python MCP SDK at D3.
"""
from mcp.server.mcpserver import MCPServer

mcp = MCPServer("FixOps MCP Server")

@mcp.tool()
def mcp_server_ping(message: str = "hello") -> dict[str, str]:
    return {"reply": message, "source": "mcp_server_ping"}

if __name__ == "__main__":
    mcp.run(
        transport="streamable-http",
        host="localhost",
        port=8090,
        stateless_http=True,
        json_response=True
    )