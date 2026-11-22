from mcp.server.mcpserver import MCPServer

def register_dev_tools(mcp: MCPServer) -> None:
    @mcp.tool()
    def mcp_server_ping(message: str = "hello") -> dict[str, str]:
        return {"reply": message, "source": "mcp_server_ping"}