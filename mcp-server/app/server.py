"""FixOps MCP Streamable HTTP technical spike.

Only a non-domain ping tool is registered. The security contract in docs/05
requires service authentication, delegated user identity, and Java-side ACL
before any real Asset/WorkOrder/Inventory tool becomes available.
"""
import os

from mcp.server.mcpserver import MCPServer
from mcp.server.transport_security import TransportSecuritySettings

mcp = MCPServer("FixOps MCP Server")


@mcp.tool()
def mcp_server_ping(message: str = "hello") -> dict[str, str]:
    return {"reply": message, "source": "mcp_server_ping"}


def main() -> None:
    # Limit access at the Compose/Docker network boundary as well.
    # Host-header / DNS-rebinding settings must be validated with pinned SDK.
    mcp.run(
        transport="streamable-http",
        host=os.getenv("MCP_BIND_HOST", "0.0.0.0"),
        port=int(os.getenv("MCP_PORT", "8090")),
        stateless_http=True,
        json_response=True,
        transport_security=TransportSecuritySettings(
            enable_dns_rebinding_protection=True,
            allowed_hosts=["mcp", "mcp:*", "localhost", "localhost:*", "127.0.0.1:*"],
            allowed_origins=[],
        ),
    )


if __name__ == "__main__":
    main()
