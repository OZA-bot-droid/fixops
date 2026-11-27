from typing import Literal, Any
from pydantic import BaseModel, ConfigDict, Field


class ToolErrorDetail(BaseModel):
    model_config = ConfigDict(extra="forbid")
    code: str = Field(min_length=1)
    retryable: bool


class ToolSuccess(BaseModel):
    model_config = ConfigDict(
        populate_by_name=True,
        extra="forbid",
    )

    ok: Literal[True] = True
    data: dict[str, Any]
    tool_name: str = Field(alias="toolName", min_length=1)
    trace_id: str = Field(alias="traceId", min_length=1)


class ToolFailure(BaseModel):
    model_config = ConfigDict(
        populate_by_name=True,
        extra="forbid",
    )

    ok: Literal[False] = False
    error: ToolErrorDetail
    tool_name: str = Field(
        alias="toolName",
        min_length=1,
    )
    trace_id: str = Field(
        alias="traceId",
        min_length=1,
    )


def build_tool_success(
    *,
    tool_name: str,
    data: dict[str, Any],
    trace_id: str,
) -> ToolSuccess:
    return ToolSuccess(
        data=data,
        toolName=tool_name,
        traceId=trace_id,
    )


def build_tool_failure(
    *,
    tool_name: str,
    code: str,
    retryable: bool,
    trace_id: str,
) -> ToolFailure:
    return ToolFailure(
        error=ToolErrorDetail(
            code=code,
            retryable=retryable,
        ),
        toolName=tool_name,
        traceId=trace_id,
    )