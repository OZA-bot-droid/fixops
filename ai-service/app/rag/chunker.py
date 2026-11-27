from app.rag.schemas import DocumentChunk, DocumentPage


def split_document_page(
    page: DocumentPage,
    *,
    chunk_size: int = 800,
    chunk_overlap: int = 100,
) -> list[DocumentChunk]:
    if chunk_size <= 0:
        raise ValueError("chunk_size must be positive")

    if chunk_overlap < 0 or chunk_overlap >= chunk_size:
        raise ValueError(
            "chunk_overlap must be between 0 and chunk_size"
        )

    text = page.text.strip()

    if not text:
        return []

    chunks: list[DocumentChunk] = []
    start = 0

    while start < len(text):
        end = min(start + chunk_size, len(text))

        content = text[start:end].strip()

        if content:
            chunks.append(
                DocumentChunk(
                    document_id=page.document_id,
                    organization_id=page.organization_id,
                    complex_id=page.complex_id,
                    filename=page.filename,
                    page_number=page.page_number,
                    chunk_index=len(chunks),
                    content=content,
                )
            )

        if end == len(text):
            break

        start = end - chunk_overlap

    return chunks


def split_document(
    pages: list[DocumentPage],
    *,
    chunk_size: int = 800,
    chunk_overlap: int = 100,
) -> list[DocumentChunk]:
    chunks: list[DocumentChunk] = []

    for page in pages:
        page_chunks = split_document_page(
            page,
            chunk_size=chunk_size,
            chunk_overlap=chunk_overlap,
        )
        chunks.extend(page_chunks)

    return chunks