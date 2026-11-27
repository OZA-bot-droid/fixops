from pathlib import PurePath

import pymupdf

from app.rag.schemas import DocumentPage


MAX_PDF_BYTES = 10 * 1024 * 1024
MAX_PDF_PAGES = 100
MAX_PAGE_CHARACTERS = 100_000
MAX_TOTAL_CHARACTERS = 1_000_000


class PDFValidationError(ValueError):
    """PDF validation or extraction failed."""


def extract_pdf_pages(
    *,
    pdf_bytes: bytes,
    document_id: int,
    organization_id: int,
    complex_id: int | None,
    filename: str,
    max_bytes: int = MAX_PDF_BYTES,
    max_pages: int = MAX_PDF_PAGES,
) -> list[DocumentPage]:

    # 파일 크기 및 제한값 검사
    if max_bytes <= 0 or max_pages <= 0:
        raise ValueError("PDF limits must be positive")

    if not pdf_bytes:
        raise PDFValidationError("PDF file is empty")

    if len(pdf_bytes) > max_bytes:
        raise PDFValidationError("PDF file is too large")

    # PDF 파일 시그니처 검사
    if not pdf_bytes.startswith(b"%PDF-"):
        raise PDFValidationError("Invalid PDF signature")

    # 파일명 검사
    if (
        not filename
        or PurePath(filename).suffix.lower() != ".pdf"
        or "/" in filename
        or "\\" in filename
    ):
        raise PDFValidationError("Invalid PDF filename")

    pages: list[DocumentPage] = []
    total_characters = 0

    try:
        with pymupdf.open(
            stream=pdf_bytes,
            filetype="pdf",
        ) as document:

            # 암호화된 PDF 거부
            if document.needs_pass:
                raise PDFValidationError(
                    "Encrypted PDF is not supported"
                )

            # 페이지 수 검사
            if document.page_count == 0:
                raise PDFValidationError(
                    "PDF contains no pages"
                )

            if document.page_count > max_pages:
                raise PDFValidationError(
                    "PDF contains too many pages"
                )

            for page_index in range(document.page_count):
                page = document.load_page(page_index)
                text = page.get_text("text").strip()

                if len(text) > MAX_PAGE_CHARACTERS:
                    raise PDFValidationError(
                        "PDF page text is too large"
                    )

                total_characters += len(text)

                if total_characters > MAX_TOTAL_CHARACTERS:
                    raise PDFValidationError(
                        "PDF total text is too large"
                    )

                # 텍스트 없는 페이지는 건너뜀
                if not text:
                    continue

                pages.append(
                    DocumentPage(
                        document_id=document_id,
                        organization_id=organization_id,
                        complex_id=complex_id,
                        filename=filename,
                        page_number=page_index + 1,
                        text=text,
                    )
                )

    except PDFValidationError:
        raise
    except (RuntimeError, ValueError) as exc:
        raise PDFValidationError(
            "PDF cannot be processed"
        ) from exc

    return pages