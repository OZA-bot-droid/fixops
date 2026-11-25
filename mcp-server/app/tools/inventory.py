"""Inventory MCP 도구 (C 파트, 이도관). 조회 전용 — AI 는 재고를 바꾸지 않는다.

설계서 : contracts/mcp/inventory-tools.md (도구 이름·입력·출력·오류 규칙)
원본   : inventory-service REST API (contracts/openapi/inventory.yaml)
흐름   : ai-service ──MCP──▶ 이 도구 ──HTTP──▶ inventory-service(8084) ──▶ DB

이 파일이 하는 일은 세 가지뿐이다.
  1) AI 가 고른 입력(검색어, 개수)으로 Inventory REST 를 부른다.
  2) Inventory 응답을 도구 형식 {ok, data, meta} 로 옮긴다.
  3) Inventory 오류를 도구 오류 {ok: false, error: {code, retryable}} 로 옮긴다.
권한 판단(어느 단지의 재고인가)은 하지 않는다. 그것은 Inventory(Java)가 매번 다시 확인한다.

등록 : server.py 에 register(mcp) 를 부르면 도구 3개가 MCP 서버에 올라간다.
       지금은 server.py 를 고치지 않았다 (하진철님 파일). 등록은 하진철님과 맞춘 뒤에 한다.
"""

from __future__ import annotations

import os
import uuid
from datetime import datetime, timezone
from typing import Annotated, Any

import httpx
from pydantic import Field

# Inventory 주소. compose 에서는 http://inventory:8084, 내 컴퓨터에서 따로 띄우면 http://localhost:8084
INVENTORY_URL = os.getenv("INVENTORY_SERVICE_URL", "http://localhost:8084").rstrip("/")
# Inventory 가 이 시간 안에 답하지 않으면 DEPENDENCY_UNAVAILABLE(재시도 가능)로 돌려준다
TIMEOUT = httpx.Timeout(float(os.getenv("INVENTORY_TIMEOUT_SECONDS", "3")))

MAX_ITEMS = 50   # 목록은 최대 50건 (설계서 1장 출력 한도)
MAX_TEXT = 200   # 문자열은 200자까지. 넘으면 자르고 truncated: true


# ======================================================================
# 사용자 정보 (위임 컨텍스트)
# ======================================================================

def _delegated_headers() -> dict[str, str] | None:
    """Inventory 에 "누가 묻는지"(회원·역할·단지)를 알리는 헤더를 만든다.

    ⏳ 미정 — 빈 자리. 서비스 신원 + 위임 컨텍스트를 어떤 형식으로 넘길지(위임 JWT? 서명된 헤더?)는
       팀장님(MCP 보안 담당)·하진철님과 정한 뒤 이 함수 하나만 채운다. 도구 3개는 고치지 않는다.

    지금은 항상 None 을 돌려준다 → 모든 도구가 UNAUTHENTICATED 로 거부한다.
    "정보가 없으니 아무 단지로 일단 조회"는 절대 하지 않는다 (남의 단지 재고가 보일 수 있다).

    주의: 회원·역할·단지 값을 도구 입력에서 받지 않는다 (docs/05: 도구 입력을 권한의 근거로 쓰지 않음).
          AI 가 "3번 단지 재고"라고 해도 단지 번호를 바꿀 길이 없게 하기 위해서다.
    """
    return None


# ======================================================================
# 도구 3개 (이름·설명·입력이 그대로 AI 에게 보인다 → 설명은 프롬프트처럼 분명하게)
# ======================================================================

def get_part_stock(
    partId: Annotated[int, Field(ge=1, description="부품 번호")],
) -> dict[str, Any]:
    """부품 하나의 현재 재고 수량과 안전재고를 조회한다. 부품 번호를 알 때 쓴다.

    결과의 belowSafetyStock 이 true 면 안전재고보다 적게 남은 것이다.
    """
    # ⏳ 원본 GET /api/inventory/parts/{partId} 는 팀장님 승인 대기 (승인되지 않으면 이 도구를 뺀다)
    result = _call("get_part_stock", f"/inventory/parts/{partId}", params=None)
    if not result["ok"]:
        return result
    p, body = result["data"], result["body"]
    item, cut = _part(p, ["sku", "name", "quantity", "unit", "safetyStock", "status"])
    item["belowSafetyStock"] = p.get("quantity", 0) < p.get("safetyStock", 0)
    data = {"partId": p.get("id"), **item}
    if cut:
        data["truncated"] = True
    return _ok(data, resource_id=f"part:{p.get('id')}", body=body)


def get_low_stock_parts(
    limit: Annotated[int, Field(ge=1, le=MAX_ITEMS, description="최대 개수 (1~50)")] = 10,
) -> dict[str, Any]:
    """안전재고보다 적게 남은 부품을 수량이 적은 순서로 조회한다. 발주가 필요한 부품을 찾을 때 쓴다."""
    result = _call("get_low_stock_parts", "/inventory/parts",
                   params={"lowStockOnly": "true", "size": limit, "sort": "quantity,asc"})
    if not result["ok"]:
        return result
    return _ok(_list(result["data"], ["sku", "name", "quantity", "safetyStock", "unit"]),
               resource_id="part:list", body=result["body"])


def search_parts(
    query: Annotated[str, Field(min_length=1, max_length=100, description="SKU 또는 부품 이름의 일부")],
    limit: Annotated[int, Field(ge=1, le=MAX_ITEMS, description="최대 개수 (1~50)")] = 10,
) -> dict[str, Any]:
    """SKU 또는 이름으로 부품을 찾고 현재 수량을 함께 보여 준다. 부품 번호를 모를 때 먼저 쓴다.

    사용 중(ACTIVE)인 부품만 찾는다.
    """
    # status 를 보내지 않으면 Inventory 기본값 ACTIVE (설계서: 비활성 부품은 AI 응답에 나오지 않음)
    result = _call("search_parts", "/inventory/parts", params={"q": query, "size": limit})
    if not result["ok"]:
        return result
    return _ok(_list(result["data"], ["sku", "name", "quantity", "unit", "status"]),
               resource_id="part:list", body=result["body"])


def register(mcp: Any) -> None:
    """MCP 서버에 도구 3개를 올린다. server.py 에서 register(mcp) 로 부른다.

    mcp.tool() 은 함수 이름을 도구 이름으로, docstring 을 도구 설명으로, 인자 타입을 입력 규칙으로 쓴다.
    """
    for tool in (get_part_stock, get_low_stock_parts, search_parts):
        mcp.tool()(tool)


# ======================================================================
# 공통: Inventory 호출과 형식 변환
# ======================================================================

# Inventory 오류 코드 → 도구 오류 (설계서 5장). retryable: 같은 요청을 잠시 뒤 다시 하면 될 수 있는가
_ERROR_MAP: dict[int, tuple[str, bool]] = {
    400: ("INVALID_INPUT", False),
    401: ("UNAUTHENTICATED", False),
    403: ("ORG_ACCESS_DENIED", False),
    404: ("RESOURCE_NOT_FOUND", False),
    503: ("DEPENDENCY_UNAVAILABLE", True),
}


def _call(tool_name: str, path: str, params: dict[str, Any] | None) -> dict[str, Any]:
    """Inventory 를 부르고, 성공이면 {ok: True, data, body}, 실패면 도구 오류를 돌려준다."""
    headers = _delegated_headers()
    if headers is None:
        # 누가 묻는지 모르면 부르지 않는다 (빈 자리 상태)
        return _error(tool_name, "UNAUTHENTICATED", retryable=False, trace_id=None)
    try:
        res = httpx.get(INVENTORY_URL + path, params=params, headers=headers, timeout=TIMEOUT)
    except httpx.TimeoutException:
        return _error(tool_name, "DEPENDENCY_UNAVAILABLE", retryable=True, trace_id=None)
    except httpx.HTTPError:
        # 연결 실패 등. 내부 예외 메시지는 도구 응답에 넣지 않는다 (설계서 5장)
        return _error(tool_name, "DEPENDENCY_UNAVAILABLE", retryable=True, trace_id=None)

    body = _json(res)
    if res.status_code == 200 and body.get("success") is True:
        return {"ok": True, "data": body.get("data") or {}, "body": body}

    code, retryable = _ERROR_MAP.get(res.status_code, ("DEPENDENCY_UNAVAILABLE", res.status_code >= 500))
    return _error(tool_name, code, retryable=retryable, trace_id=body.get("traceId"))


def _ok(data: dict[str, Any], resource_id: str, body: dict[str, Any]) -> dict[str, Any]:
    """성공 봉투. checkedAt 은 Inventory 가 응답을 만든 시각 (설계서 1장)."""
    return {
        "ok": True,
        "data": data,
        "meta": {
            "sourceService": "inventory",
            "resourceId": resource_id,
            "checkedAt": body.get("timestamp") or _now(),
            "visibility": "ORG",   # 호출자 조직 안에서만 보이는 자료
        },
    }


def _error(tool_name: str, code: str, retryable: bool, trace_id: str | None) -> dict[str, Any]:
    """오류 봉투 (docs/05 7장). 원인 메시지·스택은 넣지 않고 코드와 재시도 여부만."""
    return {
        "ok": False,
        "error": {"code": code, "retryable": retryable},
        "toolName": tool_name,
        "traceId": trace_id or str(uuid.uuid4()),
    }


def _list(page: dict[str, Any], fields: list[str]) -> dict[str, Any]:
    """Inventory 목록(PartPageResult) → 도구 목록 {items, count, truncated}."""
    content = page.get("content") or []
    items, cut = [], False
    for p in content[:MAX_ITEMS]:
        item, c = _part(p, fields)
        items.append({"partId": p.get("id"), **item})
        cut = cut or c
    total = page.get("totalElements", len(content))
    return {"items": items, "count": len(items), "truncated": cut or total > len(items)}


def _part(p: dict[str, Any], fields: list[str]) -> tuple[dict[str, Any], bool]:
    """부품 한 건에서 필요한 필드만 꺼낸다. 긴 문자열은 MAX_TEXT 에서 자른다."""
    out, cut = {}, False
    for f in fields:
        v = p.get(f)
        if isinstance(v, str) and len(v) > MAX_TEXT:
            v, cut = v[:MAX_TEXT], True
        out[f] = v
    return out, cut


def _json(res: httpx.Response) -> dict[str, Any]:
    try:
        body = res.json()
        return body if isinstance(body, dict) else {}
    except ValueError:
        return {}


def _now() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
