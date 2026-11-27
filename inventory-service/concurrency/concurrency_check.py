"""D10 원장 동시성 확인 스크립트 (테스트 코드가 아니라, 실행 중인 서버에 요청을 동시에 쏘아 보는 확인 도구).

무엇을 확인하나
  같은 순간에 요청이 몰려도 재고가 틀리지 않는지. docs/07 T12 "재고 부족/동시 차감: 음수 불가, 원장 1회".

준비 (D13 부터 로그인 토큰이 필요하다)
  1) postgres·redis·member(·gateway) 를 띄우고, inventory 를 가짜 WorkOrder 를 켜고 실행
       PowerShell:  $env:WORKORDER_FAKE="true"; python scripts/dev.py run inventory
  2) 브라우저에서 ADMIN 계정으로 로그인 → 개발자 도구(F12) → Network → 아무 /api 요청
     → Request Headers 의 Authorization: Bearer 뒤의 값(eyJ...)을 복사 (15분이면 만료)
  3) 다른 창에서 이 스크립트 실행 (BUILDING 조직이면 --complex-id 도)
       python inventory-service/concurrency/concurrency_check.py --token eyJ... --org-id 1 --complex-id 3
  3) 끝나면 ledger_check.sql 로 DB 를 직접 확인 (스크립트가 마지막에 명령을 알려 줌)

Python 기본 라이브러리만 쓴다 (설치할 것 없음). 매번 새 부품을 만들어 쓰므로 여러 번 돌려도 된다.
# 상황을 만듭니다 (동시에 몰아서 보냄)
"""

from __future__ import annotations

import argparse
import json
import sys
import threading
import time
import urllib.error
import urllib.request
from collections import Counter
from concurrent.futures import ThreadPoolExecutor

RUN = str(int(time.time() * 1000))   # 이번 실행을 구분하는 번호 (SKU·멱등 키에 붙임)


# ======================================================================
# HTTP 도우미
# ======================================================================

class Api:
    """D13: 헤더(X-Member-Id 등) 대신 로그인 JWT + ?organizationId=&complexId= 로 보낸다.
    서버가 Member 에 "이 토큰의 주인이 이 조직의 ADMIN 인가"를 확인한다 (화면과 같은 방식)."""

    def __init__(self, base_url: str, token: str, org_id: int, complex_id: int | None = None):
        self.base = base_url.rstrip("/")
        token = token.removeprefix("Bearer ").strip()
        self.headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}
        self.scope = f"organizationId={org_id}" + (f"&complexId={complex_id}" if complex_id else "")

    def call(self, method: str, path: str, body: dict | None = None, key: str | None = None):
        """요청을 보내고 (상태 코드, 응답 JSON) 을 돌려준다. 오류 응답도 예외 없이 돌려준다."""
        headers = dict(self.headers)
        if key:
            headers["Idempotency-Key"] = key
        data = json.dumps(body).encode() if body is not None else None
        url = self.base + path + ("&" if "?" in path else "?") + self.scope   # 모든 요청에 조직·단지를 붙인다
        req = urllib.request.Request(url, data=data, method=method, headers=headers)
        try:
            with urllib.request.urlopen(req, timeout=15) as res:
                return res.status, json.loads(res.read() or b"{}")
        except urllib.error.HTTPError as e:
            try:
                return e.code, json.loads(e.read() or b"{}")
            except ValueError:
                return e.code, {}

    # --- 자주 쓰는 요청 ---
    def create_part(self, name: str) -> int:
        sku = f"CC-{RUN}-{name}"
        status, body = self.call("POST", "/inventory/parts",
                                 {"sku": sku, "name": f"동시성 {name}", "safetyStock": 0},
                                 key=f"part-{RUN}-{name}")
        if status != 201:
            raise SystemExit(f"부품 등록 실패 ({status}): {body}")
        return body["data"]["id"]

    def receive(self, part_id: int, qty: int, key: str):
        return self.call("POST", "/inventory/receipts",
                         {"partId": part_id, "quantity": qty, "operationKey": key}, key=key)

    def use(self, part_id: int, qty: int, key: str, work_order_id: int = 811):
        return self.call("POST", "/inventory/usages",
                         {"workOrderId": work_order_id, "partId": part_id, "quantity": qty,
                          "operationKey": key}, key=key)

    def quantity(self, part_id: int) -> int:
        status, body = self.call("GET", f"/inventory/parts/{part_id}")
        if status != 200:
            raise SystemExit(f"수량 조회 실패 ({status}): {body}")
        return body["data"]["quantity"]


def fire_together(tasks: list) -> list:
    """여러 요청을 '같은 순간'에 보낸다.

    스레드마다 요청 준비를 끝낸 뒤 Barrier(출발선)에서 모두 기다렸다가 한꺼번에 출발한다.
    그냥 차례로 스레드를 만들면 앞 요청이 먼저 끝나 버려서 진짜 경쟁이 일어나지 않는다.
    """
    gate = threading.Barrier(len(tasks))

    def run(task):
        gate.wait()            # 모두 준비될 때까지 대기 → 동시에 출발
        return task()

    with ThreadPoolExecutor(max_workers=len(tasks)) as pool:
        return list(pool.map(run, tasks))


def codes(results) -> Counter:
    """결과를 '상태 코드 + 오류 코드' 별로 센다. 예) {201: 10, '409 INSUFFICIENT_STOCK': 10}"""
    c = Counter()
    for status, body in results:
        err = (body.get("error") or {}).get("code") if isinstance(body, dict) else None
        c[f"{status} {err}" if err else status] += 1
    return c


# ======================================================================
# 시나리오
# ======================================================================

class Report:
    def __init__(self):
        self.rows = []

    def check(self, name: str, ok: bool, expect: str, actual: str):
        self.rows.append((name, ok, expect, actual))
        mark = "✅ 통과" if ok else "❌ 실패"
        print(f"\n{mark}  {name}\n    기대: {expect}\n    실제: {actual}")

    @property
    def failed(self) -> int:
        return sum(1 for r in self.rows if not r[1])


def s1_concurrent_receipts(api: Api, rep: Report, n: int):
    """동시 입고: n 건이 동시에 +5 씩 → 모두 201, 수량 정확히 +5n."""
    part = api.create_part("s1")
    res = fire_together([lambda i=i: api.receive(part, 5, f"cc-{RUN}-s1-{i}") for i in range(n)])
    q = api.quantity(part)
    rep.check("S1 동시 입고", codes(res) == Counter({201: n}) and q == 5 * n,
              f"201 x {n}, 수량 {5 * n}", f"{dict(codes(res))}, 수량 {q}")
    return part


def s2_concurrent_usages(api: Api, rep: Report, n: int):
    """동시 사용(재고 충분): 재고 100 에서 n 건이 동시에 2 씩 → 모두 201, 수량 100 - 2n."""
    part = api.create_part("s2")
    api.receive(part, 100, f"cc-{RUN}-s2-in")
    res = fire_together([lambda i=i: api.use(part, 2, f"cc-{RUN}-s2-{i}") for i in range(n)])
    q = api.quantity(part)
    rep.check("S2 동시 사용 (재고 충분)", codes(res) == Counter({201: n}) and q == 100 - 2 * n,
              f"201 x {n}, 수량 {100 - 2 * n}", f"{dict(codes(res))}, 수량 {q}")
    return part


def s3_race_for_last_stock(api: Api, rep: Report, n: int, stock: int):
    """재고 경쟁: 재고 stock 개에 n 명이 동시에 1 개씩 → 정확히 stock 건만 성공, 나머지 409, 수량 0 (음수 없음)."""
    part = api.create_part("s3")
    api.receive(part, stock, f"cc-{RUN}-s3-in")
    res = fire_together([lambda i=i: api.use(part, 1, f"cc-{RUN}-s3-{i}") for i in range(n)])
    q = api.quantity(part)
    expect = Counter({201: stock, "409 INSUFFICIENT_STOCK": n - stock})
    rep.check("S3 마지막 재고 경쟁 (음수 불가)", codes(res) == expect and q == 0,
              f"201 x {stock}, 409 INSUFFICIENT_STOCK x {n - stock}, 수량 0",
              f"{dict(codes(res))}, 수량 {q}")
    return part


def s4_same_key_retries(api: Api, rep: Report, n: int):
    """같은 키 동시 재시도: 같은 operationKey 로 n 번 동시에 3 개 사용 → 차감은 한 번만 (201 x 1, 200 x n-1), 수량 -3."""
    part = api.create_part("s4")
    api.receive(part, 50, f"cc-{RUN}-s4-in")
    key = f"cc-{RUN}-s4-same"
    res = fire_together([lambda: api.use(part, 3, key) for _ in range(n)])
    q = api.quantity(part)
    usage_ids = {body["data"]["usageId"] for status, body in res if status in (200, 201)}
    ok = codes(res) == Counter({201: 1, 200: n - 1}) and q == 47 and len(usage_ids) == 1
    rep.check("S4 같은 키 동시 재시도 (원장 1회)", ok,
              f"201 x 1, 200 x {n - 1}, usageId 1개, 수량 47",
              f"{dict(codes(res))}, usageId {len(usage_ids)}개, 수량 {q}")
    return part


def s5_same_key_two_parts(api: Api, rep: Report):
    """같은 키를 서로 다른 부품에 동시에: 잠그는 행이 달라도 한쪽만 성공, 다른 쪽은 409 IDEMPOTENCY_KEY_REUSE."""
    a, b = api.create_part("s5a"), api.create_part("s5b")
    api.receive(a, 10, f"cc-{RUN}-s5-in-a")
    api.receive(b, 10, f"cc-{RUN}-s5-in-b")
    key = f"cc-{RUN}-s5-same"
    res = fire_together([lambda: api.use(a, 1, key), lambda: api.use(b, 1, key)])
    qa, qb = api.quantity(a), api.quantity(b)
    ok = codes(res) == Counter({201: 1, "409 IDEMPOTENCY_KEY_REUSE": 1}) and qa + qb == 19
    rep.check("S5 같은 키, 다른 부품 동시 (DB UNIQUE 가 막음)", ok,
              "201 x 1, 409 IDEMPOTENCY_KEY_REUSE x 1, 두 부품 합계 19",
              f"{dict(codes(res))}, 합계 {qa + qb} ({qa} + {qb})")
    return [a, b]


def s6_mixed(api: Api, rep: Report, n: int):
    """입고와 사용이 섞여서 동시에: 재고 20 에서 입고 +3 n 건, 사용 -2 n 건 → 수량 20 + 3n - 2n."""
    part = api.create_part("s6")
    api.receive(part, 20, f"cc-{RUN}-s6-in")
    tasks = [lambda i=i: api.receive(part, 3, f"cc-{RUN}-s6-r{i}") for i in range(n)]
    tasks += [lambda i=i: api.use(part, 2, f"cc-{RUN}-s6-u{i}") for i in range(n)]
    res = fire_together(tasks)
    q = api.quantity(part)
    expect_q = 20 + 3 * n - 2 * n
    rep.check("S6 입고·사용 섞어서 동시", codes(res) == Counter({201: 2 * n}) and q == expect_q,
              f"201 x {2 * n}, 수량 {expect_q}", f"{dict(codes(res))}, 수량 {q}")
    return part


# ======================================================================
# 실행
# ======================================================================

def preflight(api: Api):
    """서버가 떠 있고, 가짜 WorkOrder 가 켜져 있는지 먼저 확인한다."""
    try:
        status, _ = api.call("GET", "/inventory/parts?size=1")
    except urllib.error.URLError as e:
        raise SystemExit(f"서버에 연결할 수 없습니다: {api.base} ({e.reason}). inventory 서버를 먼저 실행하세요.")
    if status == 401:
        raise SystemExit("401: 토큰이 없거나 만료됐습니다. 브라우저에서 다시 복사해 --token 으로 넘기세요 (15분 만료).")
    if status == 403:
        raise SystemExit("403: 이 토큰의 주인이 이 조직(단지)의 ADMIN 이 아닙니다. --org-id / --complex-id 와 계정을 확인하세요.")
    if status == 503:
        raise SystemExit("503: 권한 확인(Member)에 연결할 수 없습니다. member 가 떠 있는지, .env 의 INVENTORY_AUTHZ_KEY 를 확인하세요.")
    if status != 200:
        raise SystemExit(f"부품 목록 조회가 {status} 입니다.")
    part = api.create_part("pre")
    api.receive(part, 1, f"cc-{RUN}-pre-in")
    status, body = api.use(part, 1, f"cc-{RUN}-pre-use")
    if status == 503:
        raise SystemExit("부품 사용이 503 입니다. 가짜 WorkOrder 가 꺼져 있습니다.\n"
                         '  PowerShell:  $env:WORKORDER_FAKE="true"; python scripts/dev.py run inventory')
    if status != 201:
        raise SystemExit(f"부품 사용 사전 확인 실패 ({status}): {body}")


def main():
    # Windows 콘솔(cp949)에서 ✅ 같은 글자가 깨지지 않도록 출력 인코딩을 UTF-8 로 맞춘다.
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")

    ap = argparse.ArgumentParser(description="D10 원장 동시성 확인")
    ap.add_argument("--base-url", default="http://localhost:8084")
    ap.add_argument("--token", required=True, help="ADMIN 계정의 로그인 JWT (브라우저 개발자 도구에서 복사)")
    ap.add_argument("--org-id", type=int, required=True, help="상단에서 고른 조직 번호")
    ap.add_argument("--complex-id", type=int, default=None, help="BUILDING 조직이면 단지 번호 (FACTORY 면 생략)")
    ap.add_argument("-n", type=int, default=20, help="한 시나리오에서 동시에 보낼 요청 수")
    args = ap.parse_args()

    api = Api(args.base_url, args.token, args.org_id, args.complex_id)
    print(f"대상: {api.base}  /  동시 요청: {args.n}건  /  실행 번호: {RUN}")
    preflight(api)

    rep = Report()
    s1_concurrent_receipts(api, rep, args.n)
    s2_concurrent_usages(api, rep, args.n)
    s3_race_for_last_stock(api, rep, args.n, stock=args.n // 2)
    s4_same_key_retries(api, rep, args.n)
    s5_same_key_two_parts(api, rep)
    s6_mixed(api, rep, args.n)

    print("\n" + "=" * 60)
    print(f"결과: {len(rep.rows) - rep.failed} / {len(rep.rows)} 통과")
    print("=" * 60)
    print("\n다음: DB 를 직접 확인하세요 (재고 = 원장의 합, 음수 없음, 원장 중복 없음)")
    print("  $OutputEncoding = [System.Text.Encoding]::UTF8")
    print("  Get-Content -Encoding UTF8 inventory-service\\concurrency\\ledger_check.sql | "
          "docker compose exec -T postgres psql -U postgres -d inventory_db")
    sys.exit(1 if rep.failed else 0)


if __name__ == "__main__":
    main()
