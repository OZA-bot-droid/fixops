import { useEffect, useState } from "react";

import {
  getRagDocuments,
  uploadRagDocument,
} from "./chatApi";


export default function RagDocumentPanel({
  organizationId,
  complexId,
  organizationKind,
  role,
}) {
  const [file, setFile] = useState(null);
  const [documents, setDocuments] = useState([]);
  const [loadingDocuments, setLoadingDocuments] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [message, setMessage] = useState("");
  const [listError, setListError] = useState("");
  const [uploadError, setUploadError] = useState("");

  const validOrganization =
    Number.isSafeInteger(Number(organizationId)) &&
    Number(organizationId) > 0;

  const validComplex =
    complexId != null &&
    Number.isSafeInteger(Number(complexId)) &&
    Number(complexId) > 0;

  const validScope =
    validOrganization &&
    (
      (organizationKind === "FACTORY" && complexId == null) ||
      (organizationKind === "BUILDING" && validComplex)
    );
  const canUpload = role === "ADMIN";

  useEffect(() => {
    let cancelled = false;

    setDocuments([]);
    setMessage("");
    setListError("");

    if (!validScope) {
      setLoadingDocuments(false);
      return;
    }

    setLoadingDocuments(true);

    getRagDocuments({ organizationId, complexId })
      .then((data) => {
        if (cancelled) return;

        console.log("[AI] 문서 목록 응답:", data);

        if (!Array.isArray(data)) {
          setListError("문서 목록 응답 형식이 올바르지 않습니다.");
          return;
        }

        setDocuments(data);
      })
      .catch((err) => {
        if (cancelled) return;

        console.error("[AI] 문서 목록 조회 실패:", {
          status: err.response?.status,
          data: err.response?.data,
        });

        setListError(
          err.response?.data?.detail ??
          "문서 목록을 불러오지 못했습니다."
        );
      })
      .finally(() => {
        if (!cancelled) {
          setLoadingDocuments(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [organizationId, complexId, validScope]);

  async function handleUpload(event) {
    event.preventDefault();

    const form = event.currentTarget;

    if (!file || !validScope || !canUpload || uploading) return;

    setUploading(true);
    setUploadError("");
    setMessage("");

    try {
      const result = await uploadRagDocument({
        file,
        organizationId,
        complexId,
      });

      const updated = await getRagDocuments({
        organizationId,
        complexId,
      });

      setDocuments(updated);
      setFile(null);
      form.reset();

      setMessage(
        `${result.filename} 등록 완료 ` +
        `(${result.page_count}페이지, ` +
        `${result.chunk_count}청크)`
      );
    } catch (err) {
      setUploadError(
        err.response?.data?.detail ??
        "PDF 업로드에 실패했습니다."
      );
    } finally {
      setUploading(false);
    }
  }

  return (
    <div className="space-y-6">
      {!validScope && (
        <div
          role="status"
          className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800"
        >
          {organizationKind === "FACTORY"
            ? "현재 공장 조직의 문서 관리 API는 단지 ID를 요구합니다. 조직 단위 문서 관리 기능을 연결한 뒤 사용할 수 있습니다."
            : "문서 관리에 사용할 단지를 먼저 선택해 주세요."}
        </div>
      )}

      {/* PDF 업로드 */}
      {canUpload && (
        <form
          onSubmit={handleUpload}
          className="space-y-4"
        >
          <label
            htmlFor="ai-document-file"
            className="block text-sm font-semibold text-fixops-text"
          >
            설비 매뉴얼 PDF
          </label>

          <input
            id="ai-document-file"
            type="file"
            accept=".pdf,application/pdf"
            disabled={uploading || !validScope}
            onChange={(event) =>
              setFile(event.target.files?.[0] ?? null)
            }
            className="block w-full rounded-lg border border-fixops-border bg-slate-50 p-3 text-sm text-fixops-text file:mr-3 file:rounded-md file:border-0 file:bg-slate-200 file:px-3 file:py-2 file:text-xs file:font-semibold"
          />

          <div className="flex justify-end">
            <button
              type="submit"
              disabled={!file || !validScope || uploading}
              className="min-h-10 rounded-lg bg-[#3169F5] px-6 text-sm font-semibold text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {uploading ? "업로드 중..." : "PDF 업로드"}
            </button>
          </div>
        </form>
      )}

      {message && (
        <p role="status" className="text-sm text-green-700">
          {message}
        </p>
      )}

      {uploadError && (
        <p role="alert" className="text-sm text-red-600">
          {String(uploadError)}
        </p>
      )}

      {/* 등록된 PDF 목록 */}
      <div className="border-t border-fixops-border pt-5">
        <h3 className="mb-4 text-sm font-bold text-fixops-text">
          등록된 문서 ({documents.length})
        </h3>

        {!validScope ? (
          <p className="rounded-lg bg-slate-50 p-4 text-sm text-fixops-muted">
            문서 조회 범위가 선택되지 않았습니다.
          </p>
        ) : loadingDocuments ? (
          <p className="rounded-lg bg-slate-50 p-4 text-sm text-fixops-muted">
            문서 목록을 불러오는 중입니다...
          </p>
        ) : listError ? (
          <p className="rounded-lg bg-red-50 p-4 text-sm text-red-600">
            {listError || "문서 목록 조회에 실패했습니다."}
          </p>
        ) : documents.length === 0 ? (
          <p className="rounded-lg bg-slate-50 p-4 text-sm text-fixops-muted">
            등록된 문서가 없습니다.
          </p>
        ) : (
          <ul className="space-y-2">
            {documents.map((document) => (
              <li
                key={document.document_id}
                className="rounded-lg border border-fixops-border bg-slate-50 p-4"
              >
                <p className="break-all text-sm font-semibold text-fixops-text">
                  {document.filename}
                </p>

                <p className="mt-1 text-xs text-fixops-muted">
                  문서 ID: {document.document_id}
                </p>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}