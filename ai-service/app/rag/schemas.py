from pydantic import BaseModel, ConfigDict, Field


class DocumentPage(BaseModel):
    model_config = ConfigDict(extra="forbid")

    document_id: int = Field(gt=0)
    organization_id: int = Field(gt=0)
    complex_id: int | None = Field(default=None, gt=0)

    filename: str = Field(min_length=1)
    page_number: int = Field(gt=0)
    text: str = Field(min_length=1)


# 문서 메타데이터와 본문을 표현하는 Pydantic 모델
class DocumentChunk(BaseModel):
    model_config = ConfigDict(extra="forbid")

    document_id: int = Field(gt=0)
    organization_id: int = Field(gt=0)
    complex_id: int | None = Field(default=None, gt=0)

    filename: str = Field(min_length=1)
    page_number: int = Field(gt=0)
    chunk_index: int = Field(ge=0)

    content: str = Field(min_length=1)