import api, { unwrap } from "../../api/client";

/**
 * 부품·재고 API (inventory-service, Gateway 경유 /api/inventory/**)
 *   부품 목록·등록·수정, 입고, 부품 사용, 작업별 사용 내역, 재고 변동 이력, 부품 요청
 *   모든 요청은 로그인 JWT(client.js 가 자동으로 붙임) + 조직·단지 범위로 보낸다 (D13)
 */

/**
 * 요청 범위 (D13): 모든 요청에 ?organizationId=&complexId= 를 붙인다.
 * 서버(InventoryAuthorization)가 이 값과 로그인 JWT 로 Member 에 "이 사람이 이 조직·단지의 구성원인가"를 확인한다.
 * complexId 는 BUILDING 조직일 때만. 값은 InventoryPage 가 상단에서 고른 조직·단지로 정해 준다.
 */
let scope = {};
export function setInventoryScope({ organizationId, complexId }) {
  scope = { organizationId, ...(complexId != null ? { complexId } : {}) };
}
const withScope = (params = {}) => ({ ...scope, ...params });

// 멱등 키: 화면이 만들어 보낸다. 같은 작업을 재시도할 때는 같은 키를 다시 보낸다 (D7 계약).
export const newOperationKey = (prefix) => `${prefix}-${crypto.randomUUID()}`;

const idem = (key) => ({ headers: { "Idempotency-Key": key }, params: withScope() });

/** 부품 목록 (GET /inventory/parts). status: ACTIVE | INACTIVE | ALL */
export async function listParts({ q = "", status = "ACTIVE", lowStockOnly = false, page = 0, size = 20, sort = "sku,asc" } = {}, signal) {
  return unwrap(await api.get("/inventory/parts", { params: withScope({ q: q || undefined, status, lowStockOnly, page, size, sort }), signal }));
}

/** 부품 등록 (POST /inventory/parts). 등록 직후 수량은 0. image 가 있으면 사진과 함께 (multipart) */
export async function createPart(values, key, image = null) {
  if (!image) return unwrap(await api.post("/inventory/parts", values, idem(key)));
  const form = new FormData();
  form.append("part", new Blob([JSON.stringify(values)], { type: "application/json" }));
  form.append("image", image, image.name);
  return unwrap(await api.post("/inventory/parts", form, { ...idem(key), timeout: 60000 }));
}

/** 부품 사진 (Blob). 사진이 없으면 404 */
export async function fetchPartImage(partId, signal) {
  const res = await api.get(`/inventory/parts/${partId}/image`, { params: withScope(), responseType: "blob", signal });
  return res.data;
}

/** 입고 (POST /inventory/receipts). ADMIN */
export async function receiveStock({ partId, quantity, note }, key) {
  return unwrap(await api.post("/inventory/receipts", { partId, quantity, note: note || undefined, operationKey: key }, idem(key)));
}

/** 부품 사용 = 재고 차감 (POST /inventory/usages). ADMIN·배정된 TECHNICIAN, 작업 IN_PROGRESS 일 때만 */
export async function recordUsage({ workOrderId, partId, quantity }, key) {
  return unwrap(await api.post("/inventory/usages", { workOrderId, partId, quantity, operationKey: key }, idem(key)));
}

/**
 * 작업별 부품 사용 내역 — ✅ GET /inventory/usages?workOrderId= (D12)
 * 화면에서 쓰는 이름으로 바꿔서 돌려준다 (사용자 이름은 Member 소유라 아직 번호만)
 */
export async function listUsages({ workOrderId } = {}, signal) {
  const page = unwrap(await api.get("/inventory/usages", { params: withScope({ workOrderId: workOrderId || undefined, size: 100 }), signal }));
  return page.content.map((u) => ({
    usageId: u.usageId, workOrderId: u.workOrderId, workOrderTitle: "", partId: u.partId,
    partName: `${u.sku} · ${u.partName}`, quantity: u.quantity, status: u.status,
    memberName: u.usedBy ? `회원 #${u.usedBy}` : "—", occurredAt: u.occurredAt,
  }));
}

/**
 * 재고 변동 이력(원장) — ✅ GET /inventory/movements?partId=&type= (D12, ADMIN)
 * partId 를 주면 그 부품 하나의 변동 이력만 (부품 목록에서 SKU·부품명을 눌렀을 때)
 * q(부품명 검색)는 서버에 없어서 받은 결과 안에서 거른다
 */
export async function listMovements({ type = "ALL", q = "", partId } = {}, signal) {
  const kw = q.trim().toLowerCase();
  const match = (m) => !kw || m.partName.toLowerCase().includes(kw);
  const page = unwrap(await api.get("/inventory/movements", {
    params: withScope({ partId: partId ?? undefined, type: type === "ALL" ? undefined : type, size: 100 }), signal,
  }));
  return page.content.map((m) => ({
    movementId: m.movementId, partId: m.partId, partName: `${m.sku} · ${m.partName}`, type: m.movementType,
    delta: m.quantityDelta, remaining: m.balanceAfter, actor: m.actorId ? `회원 #${m.actorId}` : "—",
    note: m.note || (m.workOrderId ? `WO-${m.workOrderId}` : ""), occurredAt: m.occurredAt,
  })).filter(match);
}

// ---------------------------------------------------------------------------
// 부품 요청 — ✅ /inventory/part-requests (D12)
//   TECHNICIAN 은 서버가 자기 요청만 돌려주고, ADMIN 은 조직 전체를 받는다
// ---------------------------------------------------------------------------

// 서버 응답 → 화면 이름 (요청자 이름은 Member 소유라 아직 번호만)
const toRequestView = (r) => ({
  requestId: r.requestId, partId: r.partId,
  partName: r.partId ? `${r.sku} · ${r.partName}` : r.partName,
  sku: r.sku, category: r.category, unit: r.unit, safetyStock: r.safetyStock, quantity: r.quantity,
  workOrderId: r.workOrderId,
  reason: [r.workOrderId && `WO-${r.workOrderId}`, r.reason].filter(Boolean).join(" · "),
  status: r.status, rejectReason: r.rejectReason, requester: `회원 #${r.requestedBy}`,
  requestedAt: r.requestedAt, decidedAt: r.decidedAt,
  images: r.images || [],   // 첨부 사진 정보 (내용은 fetchPartRequestImage 로 따로)
});

export async function listPartRequests({ status = "ALL" } = {}, signal) {
  const page = unwrap(await api.get("/inventory/part-requests", { params: withScope({ status: status === "ALL" ? undefined : status, size: 100 }), signal }));
  return page.content.map(toRequestView);
}

/** sku·category·unit·safetyStock: 목록에 없는 새 부품일 때 기술자가 적는 정보 (관리자가 등록할 때 미리 채워짐) */
export async function createPartRequest({ partId = null, partName, sku, category, unit, safetyStock, quantity, workOrderId, reason, images = [] }) {
  // 등록된 부품이면 partId 만 보내도 서버가 부품 정보로 채운다
  const body = partId ? { partId, quantity, workOrderId, reason } : { partName, sku, category, unit, safetyStock, quantity, workOrderId, reason };
  if (!images.length) {
    return toRequestView(unwrap(await api.post("/inventory/part-requests", body, { params: withScope() })));
  }
  // 사진이 있으면 multipart: request = 본문 JSON(형식 application/json), images = 사진들
  const form = new FormData();
  form.append("request", new Blob([JSON.stringify(body)], { type: "application/json" }));
  images.forEach((f) => form.append("images", f, f.name));
  return toRequestView(unwrap(await api.post("/inventory/part-requests", form, { params: withScope(), timeout: 60000 })));
}

/** 첨부 사진 한 장 (Blob). img 태그는 로그인 토큰을 못 붙여서 이렇게 받아 쓴다 */
export async function fetchPartRequestImage(requestId, imageId, signal) {
  const res = await api.get(`/inventory/part-requests/${requestId}/images/${imageId}`, { params: withScope(), responseType: "blob", signal });
  return res.data;
}

/**
 * 부품 요청 승인·반려 (ADMIN). 승인은 "기록만" 한다 — 재고는 바뀌지 않는다.
 * 실제 수량 증가는 관리자가 따로 입고(POST /inventory/receipts)로 등록한다 (원장에는 실제로 들어온 수량만).
 */
export async function decidePartRequest(requestId, { approve, rejectReason }) {
  const url = `/inventory/part-requests/${requestId}/${approve ? "approve" : "reject"}`;
  return toRequestView(unwrap(await api.post(url, approve ? undefined : { rejectReason }, { params: withScope() })));
}

// ---------------------------------------------------------------------------
// 오류 → 화면 문구 (docs/04 오류 코드)
// ---------------------------------------------------------------------------
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
  const status = error?.response?.status;
  const code = error?.response?.data?.error?.code;
  const serverMessage = error?.response?.data?.error?.message;
  if (status === 413) return "사진 용량이 너무 큽니다. 다른 사진을 골라 주세요.";
  // 415: 서버가 multipart(사진 첨부)를 모른다 = 사진 기능 이전 버전의 inventory 가 실행 중
  if (status === 415) return "서버가 사진 첨부를 지원하지 않습니다. inventory 서비스를 최신 코드로 다시 실행해 주세요.";
  // 입력 오류는 서버가 이유를 적어 주면 그 문장을 보여 준다 (예: 사진 형식). 기본 문구면 아래 MESSAGES 사용
  if (code === "INVALID_INPUT" && serverMessage && !serverMessage.startsWith("입력값")) return serverMessage;
  return MESSAGES[code] || error?.response?.data?.error?.message || "요청을 처리하지 못했습니다.";
}
