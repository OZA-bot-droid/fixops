import { Alert } from "../../components/ui";
import { INVENTORY_MOCK } from "./inventoryApi";

/** 화면 안 탭 (하위 메뉴 대신 한 페이지에서 전환) */
export function Tabs({ tabs, value, onChange }) {
  return (
    <div role="tablist" aria-label="부품·재고 기능" className="mb-6 flex flex-wrap gap-2 border-b border-fixops-border">
      {tabs.map((t) => {
        const active = t.key === value;
        return (
          <button key={t.key} type="button" role="tab" aria-selected={active} onClick={() => onChange(t.key)}
            className={`-mb-px border-b-2 px-4 py-3 text-[14px] font-semibold transition-colors ${active ? "border-fixops-primary text-fixops-primary" : "border-transparent text-fixops-muted hover:text-fixops-text"}`}>
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

/** 표 (Figma 24 표 모양: 연한 머리줄 + 줄 구분선) */
export function Table({ columns, rows, rowKey, empty = "표시할 데이터가 없습니다." }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[640px] border-collapse text-left text-[13px]">
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

/** ⚠️ 예시 데이터 안내 (가짜 데이터임을 화면에 항상 표시) */
export function MockNotice({ apiMissing = false }) {
  if (!INVENTORY_MOCK && !apiMissing) return null;
  return (
    <div className="mb-5">
      <Alert tone="info">
        ⚠️ 예시 데이터로 표시 중입니다.{" "}
        {apiMissing ? "이 기능의 API는 아직 없습니다 (승인 후 연결)." : "백엔드 인증 연결 후 실제 데이터로 바뀝니다."}
      </Alert>
    </div>
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
