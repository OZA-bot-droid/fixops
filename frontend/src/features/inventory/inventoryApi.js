import api, { unwrap } from "../../api/client";

/**
 * 부품·재고 API (inventory-service, Gateway 경유 /api/inventory/**)
 *
 * ⚠️ 예시 데이터 모드 (INVENTORY_MOCK)
 *   백엔드 인증 입구가 아직 JWT 방식이 아니라서(X-Member-Id 헤더 방식), 화면에서 실제로 부르면 401 이 난다.
 *   그래서 화면을 먼저 만들 수 있도록 예시 데이터로 동작하는 모드를 둔다.
 *   - 기본: 켜짐 (화면 확인용)
 *   - 끄는 법: frontend/.env.local 에  VITE_INVENTORY_MOCK=false
 *   TODO: 백엔드 인증 입구 변경 후 기본값을 끄고, 예시 데이터 코드는 삭제한다.
 *
 * API 상태
 *   ✅ 있음 : 부품 목록·등록, 입고, 부품 사용
 *   ❌ 없음 : 작업별 사용 내역, 재고 변동 이력, 부품 요청 → 예시 데이터로만 동작 (팀장님 승인 후 추가)
 */
export const INVENTORY_MOCK = import.meta.env.VITE_INVENTORY_MOCK !== "false";

// 멱등 키: 화면이 만들어 보낸다. 같은 작업을 재시도할 때는 같은 키를 다시 보낸다 (D7 계약).
export const newOperationKey = (prefix) => `${prefix}-${crypto.randomUUID()}`;

const idem = (key) => ({ headers: { "Idempotency-Key": key } });

// ---------------------------------------------------------------------------
// ⚠️ 예시 데이터 (화면 확인용) — 실제 데이터가 아니다
// ---------------------------------------------------------------------------
const now = () => new Date().toISOString();
const mock = {
  parts: [
    { id: 1, sku: "PRT-0001", name: "급수 펌프 베어링", category: "펌프", unit: "EA", safetyStock: 5, status: "ACTIVE", quantity: 14 },
    { id: 2, sku: "PRT-0002", name: "압력 센서", category: "센서", unit: "EA", safetyStock: 10, status: "ACTIVE", quantity: 8 },
    { id: 3, sku: "PRT-0003", name: "배관 연결부 (25A)", category: "배관", unit: "EA", safetyStock: 5, status: "ACTIVE", quantity: 3 },
    { id: 4, sku: "PRT-0004", name: "모터 V벨트", category: "모터", unit: "EA", safetyStock: 2, status: "ACTIVE", quantity: 0 },
    { id: 5, sku: "PRT-0005", name: "구형 밸브 (단종)", category: "밸브", unit: "EA", safetyStock: 0, status: "INACTIVE", quantity: 1 },
  ],
  usages: [
    { usageId: 101, workOrderId: 1045, workOrderTitle: "급수 펌프 교체", partId: 1, partName: "급수 펌프 베어링", quantity: 2, memberName: "김민수", occurredAt: "2026-11-26T05:10:00Z" },
    { usageId: 102, workOrderId: 1045, workOrderTitle: "급수 펌프 교체", partId: 3, partName: "배관 연결부 (25A)", quantity: 1, memberName: "김민수", occurredAt: "2026-11-26T05:12:00Z" },
    { usageId: 103, workOrderId: 1052, workOrderTitle: "지하 배수시설 점검", partId: 2, partName: "압력 센서", quantity: 1, memberName: "이현우", occurredAt: "2026-11-27T01:30:00Z" },
  ],
  movements: [
    { movementId: 9, partId: 1, partName: "급수 펌프 베어링", type: "RECEIPT", delta: 10, remaining: 16, actor: "관리자", occurredAt: "2026-11-25T00:00:00Z", note: "정기 입고" },
    { movementId: 10, partId: 1, partName: "급수 펌프 베어링", type: "USAGE", delta: -2, remaining: 14, actor: "김민수", occurredAt: "2026-11-26T05:10:00Z", note: "WO-1045" },
    { movementId: 11, partId: 3, partName: "배관 연결부 (25A)", type: "USAGE", delta: -1, remaining: 3, actor: "김민수", occurredAt: "2026-11-26T05:12:00Z", note: "WO-1045" },
    { movementId: 12, partId: 2, partName: "압력 센서", type: "USAGE", delta: -1, remaining: 8, actor: "이현우", occurredAt: "2026-11-27T01:30:00Z", note: "WO-1052" },
  ],
  requests: [
    { requestId: 1, partId: 4, partName: "PRT-0004 · 모터 V벨트", quantity: 2, reason: "WO-1060 · 교체용, 재고 없음", requester: "김민수", status: "REQUESTED", requestedAt: "2026-11-27T00:40:00Z" },
    { requestId: 2, partId: 3, partName: "PRT-0003 · 배관 연결부 (25A)", quantity: 5, reason: "안전 재고 미만", requester: "이현우", status: "APPROVED", requestedAt: "2026-11-26T02:10:00Z", decidedAt: "2026-11-26T04:00:00Z" },
    { requestId: 3, partId: null, partName: "소방 펌프 씰 키트", sku: "PRT-0010", category: "펌프", unit: "SET", safetyStock: 1, quantity: 1, reason: "WO-1058 · 목록에 없는 부품", requester: "김민수", status: "REJECTED", rejectReason: "기존 PRT-0001로 대체 가능", requestedAt: "2026-11-25T06:00:00Z", decidedAt: "2026-11-25T08:00:00Z" },
  ],
};
const wait = (v) => new Promise((r) => setTimeout(() => r(structuredClone(v)), 250));

// ---------------------------------------------------------------------------
// ✅ 있는 API
// ---------------------------------------------------------------------------

/** 부품 목록 (GET /inventory/parts). status: ACTIVE | INACTIVE | ALL */
export async function listParts({ q = "", status = "ACTIVE", lowStockOnly = false, page = 0, size = 20, sort = "sku,asc" } = {}, signal) {
  if (INVENTORY_MOCK) {
    const kw = q.trim().toLowerCase();
    const content = mock.parts
      .filter((p) => status === "ALL" || p.status === status)
      .filter((p) => !lowStockOnly || p.quantity < p.safetyStock)
      .filter((p) => !kw || p.sku.toLowerCase().includes(kw) || p.name.toLowerCase().includes(kw));
    return wait({ content, page: 0, size, totalElements: content.length });
  }
  return unwrap(await api.get("/inventory/parts", { params: { q: q || undefined, status, lowStockOnly, page, size, sort }, signal }));
}

/** 부품 등록 (POST /inventory/parts). 등록 직후 수량은 0 */
export async function createPart(values, key) {
  if (INVENTORY_MOCK) {
    if (mock.parts.some((p) => p.sku === values.sku)) throw mockError(409, "DUPLICATE_SKU", "이미 등록된 SKU 입니다.");
    const part = { id: Math.max(...mock.parts.map((p) => p.id)) + 1, status: "ACTIVE", quantity: 0, ...values };
    mock.parts.push(part);
    return wait(part);
  }
  return unwrap(await api.post("/inventory/parts", values, idem(key)));
}

/** 입고 (POST /inventory/receipts). ADMIN */
export async function receiveStock({ partId, quantity, note }, key) {
  if (INVENTORY_MOCK) {
    const part = mock.parts.find((p) => p.id === partId);
    part.quantity += quantity;
    mock.movements.unshift({ movementId: Date.now(), partId, partName: part.name, type: "RECEIPT", delta: quantity, remaining: part.quantity, actor: "관리자", occurredAt: now(), note: note || "" });
    return wait({ partId, quantityDelta: quantity, remainingQuantity: part.quantity, occurredAt: now() });
  }
  return unwrap(await api.post("/inventory/receipts", { partId, quantity, note: note || undefined, operationKey: key }, idem(key)));
}

/** 부품 사용 = 재고 차감 (POST /inventory/usages). ADMIN·배정된 TECHNICIAN, 작업 IN_PROGRESS 일 때만 */
export async function recordUsage({ workOrderId, partId, quantity }, key) {
  if (INVENTORY_MOCK) {
    const part = mock.parts.find((p) => p.id === partId);
    if (part.quantity < quantity) throw mockError(409, "INSUFFICIENT_STOCK", "재고가 부족합니다.");
    part.quantity -= quantity;
    mock.usages.unshift({ usageId: Date.now(), workOrderId, workOrderTitle: `작업 ${workOrderId}`, partId, partName: part.name, quantity, memberName: "나", occurredAt: now() });
    mock.movements.unshift({ movementId: Date.now(), partId, partName: part.name, type: "USAGE", delta: -quantity, remaining: part.quantity, actor: "나", occurredAt: now(), note: `WO-${workOrderId}` });
    return wait({ workOrderId, partId, quantity, remainingQuantity: part.quantity, occurredAt: now() });
  }
  return unwrap(await api.post("/inventory/usages", { workOrderId, partId, quantity, operationKey: key }, idem(key)));
}

// ---------------------------------------------------------------------------
// ❌ 아직 없는 API — 예시 데이터로만 동작 (승인 후 실제 호출로 교체)
// ---------------------------------------------------------------------------

/** TODO(D12): GET /inventory/usages?workOrderId= (팀장님 승인 대기) */
export async function listUsages({ workOrderId } = {}) {
  const items = mock.usages.filter((u) => !workOrderId || String(u.workOrderId) === String(workOrderId));
  return wait(items);
}

/**
 * TODO: GET /inventory/movements?partId=&type= (승인 대기)
 * partId 를 주면 그 부품 하나의 변동 이력만 (부품 목록에서 SKU·부품명을 눌렀을 때)
 */
export async function listMovements({ type = "ALL", q = "", partId } = {}) {
  const kw = q.trim().toLowerCase();
  return wait(mock.movements.filter((m) =>
    (partId == null || m.partId === partId) &&
    (type === "ALL" || m.type === type) &&
    (!kw || m.partName.toLowerCase().includes(kw))));
}

/** TODO: 부품 요청 API (설계 전). TECHNICIAN 이 재고가 없거나 부족한 부품을 관리자에게 요청 */
export async function listPartRequests({ status = "ALL" } = {}) {
  return wait(mock.requests.filter((r) => status === "ALL" || r.status === status));
}
/** sku·category·unit·safetyStock: 목록에 없는 새 부품일 때 기술자가 적는 정보 (관리자가 등록할 때 미리 채워짐) */
export async function createPartRequest({ partId = null, partName, sku, category, unit, safetyStock, quantity, reason }) {
  const req = { requestId: Date.now(), partId, partName, sku, category, unit, safetyStock, quantity, reason, requester: "나", status: "REQUESTED", requestedAt: now() };
  mock.requests.unshift(req);
  return wait(req);
}

/**
 * TODO: 부품 요청 승인·반려 (ADMIN). 승인은 "기록만" 한다 — 재고는 바뀌지 않는다.
 * 실제 수량 증가는 관리자가 따로 입고(POST /inventory/receipts)로 등록한다 (원장에는 실제로 들어온 수량만).
 */
export async function decidePartRequest(requestId, { approve, rejectReason }) {
  const req = mock.requests.find((r) => r.requestId === requestId);
  if (req.status !== "REQUESTED") throw mockError(409, "INVALID_STATE_TRANSITION", "이미 처리된 요청입니다.");
  Object.assign(req, approve ? { status: "APPROVED" } : { status: "REJECTED", rejectReason }, { decidedAt: now() });
  return wait(req);
}

// ---------------------------------------------------------------------------
// 오류 → 화면 문구 (docs/04 오류 코드)
// ---------------------------------------------------------------------------
function mockError(status, code, message) {
  const e = new Error(message);
  e.response = { status, data: { error: { code, message } } };
  return e;
}
const MESSAGES = {
  UNAUTHENTICATED: "로그인이 필요합니다.",
  ORG_ACCESS_DENIED: "이 기능을 사용할 권한이 없습니다.",
  RESOURCE_NOT_FOUND: "대상을 찾을 수 없습니다.",
  DUPLICATE_SKU: "이미 등록된 SKU 입니다.",
  PART_INACTIVE: "비활성 부품에는 입고할 수 없습니다.",
  INSUFFICIENT_STOCK: "재고가 부족합니다.",
  IDEMPOTENCY_KEY_REUSE: "같은 요청 키가 다른 내용으로 사용되었습니다. 새로 시도해 주세요.",
  WORK_ORDER_NOT_IN_PROGRESS: "진행 중인 작업에만 부품을 사용할 수 있습니다.",
  DEPENDENCY_UNAVAILABLE: "작업 정보를 확인할 수 없어 처리하지 않았습니다. 잠시 후 다시 시도해 주세요.",
  INVALID_INPUT: "입력값을 확인해 주세요.",
  INVALID_STATE_TRANSITION: "이미 처리된 요청입니다.",
};
export function inventoryErrorMessage(error) {
  const code = error?.response?.data?.error?.code;
  return MESSAGES[code] || error?.response?.data?.error?.message || "요청을 처리하지 못했습니다.";
}
