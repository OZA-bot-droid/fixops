import httpx

from app.clients.http_client import FixOpsHTTPClient
from app.core.tool_error_mapper import map_http_error
from app.schemas.tool_result import (
    ToolSuccess,
    ToolFailure,
    build_tool_success,
)


async def execute_http_tool(
    *,
    http_client: FixOpsHTTPClient,
    tool_name: str,
    path: str,
    trace_id: str,
    headers: dict[str, str] | None = None,
) -> ToolSuccess | ToolFailure:

    try:
        data = await http_client.get(
            path,
            headers=headers,
        )

        return build_tool_success(
            tool_name=tool_name,
            data=data,
            trace_id=trace_id,
        )

    except httpx.HTTPError as exc:
        return map_http_error(
            exc=exc,
            tool_name=tool_name,
            trace_id=trace_id,
        )