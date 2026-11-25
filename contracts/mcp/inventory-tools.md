# Inventory MCP Tool 계약 (초안)

기준: `docs/05`(AI·MCP 계약) 2장, `contracts/openapi/inventory.yaml`

## 1. 공통 규칙

- **조회 전용.** AI는 재고를 차감하거나 입고하지 않는다. 쓰기 도구는 만들지 않는다(`docs/05` 1장).
- **권한을 인자로 받지 않는다.** `orgId`, `role`, `memberId`는 Tool 입력에 두지 않는다. 호출자의 위임 컨텍스트에서 Inventory가 다시 확인한다(`docs/05` 2장).
- **결과에 메타데이터를 붙인다.** `sourceService`, `resourceId`, `checkedAt`, `visibility`.
- **출력 한도.** 목록은 최대 50건, 문자열 필드는 200자까지. 초과 시 잘라내고 `truncated: true`를 붙인다.
- 원본은 모두 Inventory REST API. MCP Server는 그 응답을 도구 형식으로 옮기기만 한다.

### 공통 응답 봉투

```json
{
  "ok": true,
  "data": { },
  "meta": {
    "sourceService": "inventory",
    "resourceId": "part:92",
    "checkedAt": "2026-11-23T05:40:00Z",
    "visibility": "ORG"
  }
}
```

| 필드 | 뜻 |
|---|---|
| `sourceService` | 항상 `inventory` |
| `resourceId` | `part:{partId}` 형식. 목록이면 `part:list` |
| `checkedAt` | Inventory가 응답을 만든 시각 (ISO8601 UTC Z) |
| `visibility` | `ORG` 고정. 호출자 조직 안에서만 보이는 자료라는 뜻 |

## 2. get_part_stock

부품 하나의 현재 재고를 조회한다.

**입력**
```json
{
  "type": "object",
  "properties": { "partId": { "type": "integer", "minimum": 1 } },
  "required": ["partId"],
  "additionalProperties": false
}
```

**출력 예시**
```json
{
  "ok": true,
  "data": {
    "partId": 92,
    "sku": "BOLT-M8",
    "name": "볼트 M8",
    "quantity": 98,
    "unit": "EA",
    "safetyStock": 10,
    "belowSafetyStock": false,
    "status": "ACTIVE"
  },
  "meta": { "sourceService": "inventory", "resourceId": "part:92",
            "checkedAt": "2026-11-23T05:40:00Z", "visibility": "ORG" }
}
```

**원본:** `GET /api/inventory/parts/{partId}` — **신규 필요** (`docs/04` 필수 목록에 없음, 아래 5장 참고)

## 3. get_low_stock_parts

안전재고보다 적게 남은 부품을 조회한다.

**입력**
```json
{
  "type": "object",
  "properties": { "limit": { "type": "integer", "minimum": 1, "maximum": 50, "default": 10 } },
  "additionalProperties": false
}
```

**출력 예시**
```json
{
  "ok": true,
  "data": {
    "items": [
      { "partId": 7, "sku": "NUT-M8", "name": "너트 M8", "quantity": 2, "safetyStock": 20, "unit": "EA" }
    ],
    "count": 1,
    "truncated": false
  },
  "meta": { "sourceService": "inventory", "resourceId": "part:list",
            "checkedAt": "2026-11-23T05:40:00Z", "visibility": "ORG" }
}
```

**원본:** `GET /api/inventory/parts?lowStockOnly=true&size={limit}&sort=quantity,asc`

## 4. search_parts

SKU 또는 이름으로 부품을 찾는다.

**입력**
```json
{
  "type": "object",
  "properties": {
    "query": { "type": "string", "minLength": 1, "maxLength": 100 },
    "limit": { "type": "integer", "minimum": 1, "maximum": 50, "default": 10 }
  },
  "required": ["query"],
  "additionalProperties": false
}
```

**출력 예시**
```json
{
  "ok": true,
  "data": {
    "items": [
      { "partId": 92, "sku": "BOLT-M8", "name": "볼트 M8", "quantity": 98, "unit": "EA", "status": "ACTIVE" }
    ],
    "count": 1,
    "truncated": false
  },
  "meta": { "sourceService": "inventory", "resourceId": "part:list",
            "checkedAt": "2026-11-23T05:40:00Z", "visibility": "ORG" }
}
```

**원본:** `GET /api/inventory/parts?q={query}&size={limit}`

검색 대상은 ACTIVE 부품만. 비활성 부품은 AI 응답에 나오지 않는다.

## 5. 오류 응답

형식은 `docs/05` 7장을 따른다.

```json
{ "ok": false, "error": { "code": "RESOURCE_NOT_FOUND", "retryable": false },
  "toolName": "get_part_stock", "traceId": "example" }
```

| Inventory REST | Tool 오류 코드 | retryable |
|---|---|---|
| 403 `ORG_ACCESS_DENIED` | `ORG_ACCESS_DENIED` | false |
| 404 `RESOURCE_NOT_FOUND` | `RESOURCE_NOT_FOUND` | false |
| 503 `DEPENDENCY_UNAVAILABLE` | `DEPENDENCY_UNAVAILABLE` | true |
| 시간 초과 | `DEPENDENCY_UNAVAILABLE` | true |

내부 예외 메시지와 스택 추적은 도구 응답에 넣지 않는다.

## 6. 미결 항목

1. **`GET /api/inventory/parts/{partId}` 신규 추가** — `get_part_stock`에 필요한데 `docs/04` 필수 API 목록에는 컬렉션 경로(`GET/POST /api/inventory/parts`)만 있다. D9에 구현은 해 두었고(PartController, inventory.yaml에 "승인 대기" 표시) **팀장 승인 필요**. 승인되지 않으면 API와 `get_part_stock` 도구를 함께 뺀다.
2. **MCP Server와 Inventory 사이의 인증 방식** — 서비스 신원 + 사용자 위임 컨텍스트를 어떤 형식으로 전달할지 (팀장·하진철 협의). 코드에서는 `_delegated_headers()` 한 곳이 빈 자리로 남아 있고, 정해지기 전까지 모든 도구가 `UNAUTHENTICATED`로 거부한다.
3. ~~MCP SDK 미확정~~ (D9 확정) — `mcp` 2.x(`mcp-server/pyproject.toml`). 구현은 `mcp-server/app/tools/inventory.py`, 등록은 `register(mcp)`. `server.py` 연결은 하진철님과 맞춘 뒤에 한다.
4. **`visibility` 값 집합** — 현재 `ORG` 하나만 쓴다. 다른 서비스가 다른 값을 쓰면 맞춘다.
