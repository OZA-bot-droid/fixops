
/**
 * 화면 안 탭 (하위 메뉴 대신 한 페이지에서 전환)
 * 좁은 화면에서는 다음 줄로 넘어간다 (가로 스크롤 없음). 탭 이름은 중간에 끊기지 않는다
 */
export function Tabs({ tabs, value, onChange }) {
  return (
    <div role="tablist" aria-label="부품·재고 기능" className="mb-6 flex flex-wrap gap-x-1 border-b border-fixops-border sm:gap-x-2">
      {tabs.map((t) => {
        const active = t.key === value;
        return (
          <button key={t.key} type="button" role="tab" aria-selected={active} onClick={() => onChange(t.key)}
            className={`-mb-px shrink-0 whitespace-nowrap border-b-2 px-3 py-3 text-[13px] font-semibold transition-colors sm:px-4 sm:text-[14px] ${active ? "border-fixops-primary text-fixops-primary" : "border-transparent text-fixops-muted hover:text-fixops-text"}`}>
            {t.label}
          </button>
        );
      })}
    </div>
  );
}

/** 필터 알약 버튼 (Figma 24: 전체 / 진행 중 / 완료 모양) */
export function Pills({ options, value, onChange }) {
  return (
    <div className="flex flex-wrap gap-2">
      {options.map((o) => (
        <button key={o.value} type="button" onClick={() => onChange(o.value)}
          className={`rounded-full px-4 py-2 text-[12px] font-semibold ${o.value === value ? "bg-blue-50 text-fixops-primary" : "bg-fixops-background text-fixops-muted hover:text-fixops-text"}`}>
          {o.label}
        </button>
      ))}
    </div>
  );
}

/**
 * 표 (Figma 24 표 모양: 연한 머리줄 + 줄 구분선)
 *
 * 반응형: 같은 데이터를 화면 너비에 따라 두 가지로 보여 준다
 *   md(768px) 이상 : 표
 *   그보다 좁으면  : 카드 목록. 첫 번째 칸을 제목으로, 나머지는 "이름 : 값" 줄로, 버튼 칸(이름 없는 칸)은 맨 아래
 *   표를 가로로 밀어서 보는 것보다 한 손으로 위아래로만 넘기면 되어서 모바일에서 읽기 쉽다
 */
export function Table({ columns, rows, rowKey, empty = "표시할 데이터가 없습니다." }) {
  const cell = (c, r) => (c.render ? c.render(r) : r[c.key]);
  const [first, ...rest] = columns;
  const fields = rest.filter((c) => c.label);
  const actions = rest.filter((c) => !c.label);
  return (
    <>
      {/* 모바일: 카드 목록 */}
      <ul className="divide-y divide-fixops-border md:hidden">
        {rows.length === 0 && <li className="px-2 py-10 text-center text-[13px] text-fixops-muted">{empty}</li>}
        {rows.map((r) => (
          <li key={rowKey(r)} className="grid gap-2 px-2 py-4 text-[13px]">
            <div className="font-semibold">{cell(first, r)}</div>
            <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1">
              {fields.map((c) => (
                <div key={c.key} className="contents">
                  <dt className="text-[12px] text-fixops-muted">{c.label}</dt>
                  <dd className="min-w-0 break-words">{cell(c, r)}</dd>
                </div>
              ))}
            </dl>
            {actions.map((c) => <div key={c.key} className="pt-1">{cell(c, r)}</div>)}
          </li>
        ))}
      </ul>

      {/* 태블릿·PC: 표 */}
      <div className="hidden overflow-x-auto md:block">
      <table className="w-full border-collapse text-left text-[13px]">
        <thead>
          <tr className="bg-fixops-background text-[12px] text-fixops-muted">
            {columns.map((c) => <th key={c.key} className={`px-4 py-3 font-semibold ${c.className || ""}`}>{c.label}</th>)}
          </tr>
        </thead>
        <tbody>
          {rows.length === 0 && <tr><td colSpan={columns.length} className="px-4 py-12 text-center text-fixops-muted">{empty}</td></tr>}
          {rows.map((r) => (
            <tr key={rowKey(r)} className="border-b border-fixops-border last:border-0">
              {columns.map((c) => <td key={c.key} className={`px-4 py-4 ${c.className || ""}`}>{c.render ? c.render(r) : r[c.key]}</td>)}
            </tr>
          ))}
        </tbody>
      </table>
      </div>
    </>
  );
}

/** 재고 상태 뱃지: 비활성 / 없음 / 부족(안전재고 미만) / 정상 */
export function StockBadge({ part }) {
  const [label, tone] =
    part.status === "INACTIVE" ? ["비활성", "bg-slate-100 text-slate-600"]
    : part.quantity === 0 ? ["재고 없음", "bg-red-50 text-red-700"]
    : part.quantity < part.safetyStock ? ["부족", "bg-amber-50 text-amber-700"]
    : ["정상", "bg-emerald-50 text-emerald-700"];
  return <span className={`inline-flex rounded-full px-3 py-1 text-xs font-semibold ${tone}`}>{label}</span>;
}

/**
 * 요약 카드 (Figma 36: 배정 작업 12건 ● 현황)
 * onClick 을 주면 눌러서 그 값에 해당하는 목록으로 조회할 수 있다. active 면 파란 테두리.
 */
export function StatCard({ label, value, tone = "text-fixops-success", onClick, active = false }) {
  const body = (
    <>
      <p className="text-[12px] text-fixops-muted">{label}</p>
      <div className="mt-3 flex items-end justify-between">
        <p className="text-[24px] font-bold">{value}</p>
        <span className={`text-[11px] font-semibold ${tone}`}>{onClick ? (active ? "● 조회 중" : "● 조회") : "● 현황"}</span>
      </div>
    </>
  );
  const box = `rounded-2xl border bg-white p-5 text-left ${active ? "border-fixops-primary ring-2 ring-blue-100" : "border-fixops-border"}`;
  if (!onClick) return <div className={box}>{body}</div>;
  return (
    <button type="button" onClick={onClick} aria-pressed={active}
      className={`${box} transition hover:border-fixops-primary hover:shadow-sm`}>
      {body}
    </button>
  );
}

/** 원장 구분 뱃지: 입고 / 사용 / 반납 / 조정 */
const MOVEMENT_TYPES = {
  RECEIPT: ["입고", "bg-blue-50 text-fixops-primary"],
  USAGE: ["사용", "bg-amber-50 text-amber-700"],
  STOCK_RETURN: ["반납", "bg-emerald-50 text-emerald-700"],
  ADJUSTMENT: ["조정", "bg-slate-100 text-slate-600"],
};
export function MovementBadge({ type }) {
  const [label, tone] = MOVEMENT_TYPES[type] || [type, "bg-slate-100 text-slate-600"];
  return <span className={`inline-flex rounded-full px-3 py-1 text-xs font-semibold ${tone}`}>{label}</span>;
}
export function Delta({ value }) {
  return <b className={value > 0 ? "text-fixops-primary" : "text-fixops-danger"}>{value > 0 ? `+${value}` : value}</b>;
}

/** 부품 요청 상태 뱃지 */
const REQUEST_STATUS = {
  REQUESTED: ["요청됨", "bg-blue-50 text-fixops-primary"],
  APPROVED: ["승인", "bg-emerald-50 text-emerald-700"],
  REJECTED: ["반려", "bg-red-50 text-red-700"],
};
export function RequestBadge({ status }) {
  const [label, tone] = REQUEST_STATUS[status] || [status, "bg-slate-100 text-slate-600"];
  return <span className={`inline-flex rounded-full px-3 py-1 text-xs font-semibold ${tone}`}>{label}</span>;
}

export const formatTime = (iso) =>
  iso ? new Date(iso).toLocaleString("ko-KR", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" }) : "—";

/**
 * 쪽 번호 (쇼핑몰 검색 결과처럼 숫자로 나눠 보기)
 *   ‹ 1 2 3 … 10 ›   번호는 10개씩 묶어서 보여 주고, ‹ › 는 이전·다음 쪽
 *   page: 0부터 (서버의 page 와 같음). 화면에는 1부터 보인다
 */
export function Pagination({ page, size, total, onChange }) {
  const last = Math.max(0, Math.ceil(total / size) - 1);
  if (last === 0) return null;                       // 한 쪽뿐이면 숨김
  const start = Math.floor(page / 10) * 10;          // 지금 묶음의 첫 쪽
  const end = Math.min(last, start + 9);
  const nums = Array.from({ length: end - start + 1 }, (_, i) => start + i);
  const base = "grid h-9 min-w-9 place-items-center rounded-lg px-2 text-[13px] font-semibold";
  const arrow = `${base} text-fixops-muted enabled:hover:bg-fixops-background disabled:opacity-30`;
  return (
    <nav aria-label="쪽 이동" className="mt-4 flex flex-wrap items-center justify-center gap-1">
      <button type="button" className={arrow} disabled={page === 0} onClick={() => onChange(page - 1)} aria-label="이전 쪽">‹</button>
      {start > 0 && <button type="button" className={`${base} text-fixops-muted hover:bg-fixops-background`} onClick={() => onChange(start - 1)} aria-label="이전 10쪽">…</button>}
      {nums.map((n) => (
        <button key={n} type="button" onClick={() => onChange(n)} aria-current={n === page ? "page" : undefined}
          className={`${base} ${n === page ? "bg-fixops-primary text-white" : "text-fixops-text hover:bg-fixops-background"}`}>{n + 1}</button>
      ))}
      {end < last && <button type="button" className={`${base} text-fixops-muted hover:bg-fixops-background`} onClick={() => onChange(end + 1)} aria-label="다음 10쪽">…</button>}
      <button type="button" className={arrow} disabled={page === last} onClick={() => onChange(page + 1)} aria-label="다음 쪽">›</button>
    </nav>
  );
}
