import { useEffect, useId, useRef } from "react";
import RagDocumentPanel from "./RagDocumentPanel";

export default function DocumentManagementModal({
  open,
  onClose,
  organizationId,
  complexId,
  organizationKind,
  role,
}) {
  const titleId = useId();
  const dialogRef = useRef(null);
  const closeButtonRef = useRef(null);

  useEffect(() => {
    if (!open) return;

    const previousFocus = document.activeElement;
    const previousOverflow = document.body.style.overflow;

    document.body.style.overflow = "hidden";
    closeButtonRef.current?.focus();

    function handleKeyDown(event) {
      if (event.key === "Escape") {
        event.preventDefault();
        onClose();
        return;
      }

      if (event.key !== "Tab") return;

      const dialog = dialogRef.current;
      if (!dialog) return;

      const elements = Array.from(
        dialog.querySelectorAll(
          'button:not([disabled]), input:not([disabled]), ' +
          'select:not([disabled]), textarea:not([disabled]), ' +
          'a[href], [tabindex]:not([tabindex="-1"])'
        )
      ).filter((element) => element.getClientRects().length > 0);

      if (elements.length === 0) {
        event.preventDefault();
        dialog.focus();
        return;
      }

      const first = elements[0];
      const last = elements[elements.length - 1];

      if (
        event.shiftKey &&
        (document.activeElement === first ||
          document.activeElement === dialog)
      ) {
        event.preventDefault();
        last.focus();
      } else if (
        !event.shiftKey &&
        (document.activeElement === last ||
          !dialog.contains(document.activeElement))
      ) {
        event.preventDefault();
        first.focus();
      }
    }

    document.addEventListener("keydown", handleKeyDown);

    return () => {
      document.removeEventListener("keydown", handleKeyDown);
      document.body.style.overflow = previousOverflow;

      if (previousFocus?.isConnected) {
        previousFocus.focus();
      }
    };
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) {
          onClose();
        }
      }}
    >
      <section
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        className="flex max-h-[90dvh] w-full max-w-[560px] flex-col overflow-hidden rounded-2xl bg-white shadow-2xl"
      >
        {/* Header */}
        <header className="flex shrink-0 items-start justify-between gap-4 px-7 pb-5 pt-7">
          <div>
            <h2
              id={titleId}
              className="text-xl font-bold text-fixops-text"
            >
              PDF 문서 관리
            </h2>

            <p className="mt-2 text-xs text-fixops-muted">
              AI 점검에 사용할 설비 매뉴얼을 관리합니다.
            </p>
          </div>

          <button
            ref={closeButtonRef}
            type="button"
            onClick={onClose}
            aria-label="닫기"
            className="text-xl text-fixops-muted hover:text-fixops-text"
          >
            ×
          </button>
        </header>

        {/* Content */}
        <div className="min-h-0 flex-1 overflow-y-auto border-t border-fixops-border px-7 py-6">
          <RagDocumentPanel
            organizationId={organizationId}
            complexId={complexId}
            organizationKind={organizationKind}
            role={role}
          />
        </div>

        {/* Footer */}
        <footer className="flex shrink-0 justify-end border-t border-fixops-border px-7 py-5">
          <button
            type="button"
            onClick={onClose}
            className="min-h-10 rounded-lg bg-slate-100 px-6 text-sm font-semibold text-fixops-text hover:bg-slate-200"
          >
            닫기
          </button>
        </footer>
      </section>
    </div>
  );
}