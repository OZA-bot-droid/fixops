export default function RagSources({ sources = [] }) {
  if (!sources.length) return null;

  return (
    <div className="mt-3 border-t border-fixops-border pt-3">
      <p className="mb-2 text-xs font-semibold">
        참고 문서
      </p>

      <ul className="space-y-2">
        {sources.map((source) => (
          <li
            key={`${source.document_id}-${source.page_number}`}
            className="rounded-lg bg-white px-3 py-2 text-xs"
          >
            <p className="break-all font-medium">
              {source.filename}
            </p>

            <p className="text-fixops-muted">
              {source.page_number}페이지
            </p>
          </li>
        ))}
      </ul>
    </div>
  );
}