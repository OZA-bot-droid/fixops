from datetime import datetime
from pydantic import BaseModel, Field


class ErrorDetail(BaseModel):
    code: str
    message: str


class ErrorResponse(BaseModel):
    success: bool = False
    error: ErrorDetail
    trace_id: str = Field(alias="traceId")
    timestamp: datetime
