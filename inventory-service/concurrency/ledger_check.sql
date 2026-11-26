-- D10 원장 동시성 DB 확인 (concurrency_check.py 를 돌린 뒤 실행)
-- 실행 (PowerShell, 저장소 맨 위 폴더에서):
--   $OutputEncoding = [System.Text.Encoding]::UTF8   (한글이 ? 로 깨지지 않게, PowerShell 창마다 한 번)
--   Get-Content -Encoding UTF8 inventory-service\concurrency\ledger_check.sql | docker compose exec -T postgres psql -U postgres -d inventory_db
--
-- 다섯 개 모두 "0 rows" 가 나와야 정상이다. 한 줄이라도 나오면 그 부품·키에서 정합성이 깨진 것이다.

\echo '1) 재고와 원장의 합이 다른 부품 (재고 = 원장의 합이어야 함)'
SELECT p.id AS part_id, p.sku, b.quantity AS balance,
       COALESCE(SUM(m.quantity_delta), 0) AS ledger_sum
  FROM part p
  JOIN stock_balance b ON b.part_id = p.id
  LEFT JOIN stock_movement m ON m.part_id = p.id
 GROUP BY p.id, p.sku, b.quantity
HAVING b.quantity <> COALESCE(SUM(m.quantity_delta), 0);

\echo '2) 음수 재고 (CHECK 제약이 있어 0 이어야 함)'
SELECT part_id, quantity FROM stock_balance WHERE quantity < 0;

\echo '3) 같은 멱등 키로 원장이 두 번 이상 (UNIQUE 제약이 있어 0 이어야 함)'
SELECT organization_id, operation_key, COUNT(*) AS cnt
  FROM stock_movement
 GROUP BY organization_id, operation_key
HAVING COUNT(*) > 1;

\echo '4) 사용 기록(part_usage)은 있는데 원장(USAGE)이 없는 것'
SELECT u.id AS usage_id, u.operation_key
  FROM part_usage u
  LEFT JOIN stock_movement m
    ON m.organization_id = u.organization_id
   AND m.operation_key   = u.operation_key
   AND m.movement_type   = 'USAGE'
 WHERE m.id IS NULL;

\echo '5) 원장(USAGE)은 있는데 사용 기록(part_usage)이 없는 것'
SELECT m.id AS movement_id, m.operation_key
  FROM stock_movement m
  LEFT JOIN part_usage u
    ON u.organization_id = m.organization_id
   AND u.operation_key   = m.operation_key
 WHERE m.movement_type = 'USAGE' AND u.id IS NULL;

\echo '참고) 이번 확인으로 만든 부품들의 잔액과 원장 합 (CC- 로 시작하는 SKU)'
SELECT p.sku, b.quantity AS balance, COALESCE(SUM(m.quantity_delta), 0) AS ledger_sum, COUNT(m.id) AS movements
  FROM part p
  JOIN stock_balance b ON b.part_id = p.id
  LEFT JOIN stock_movement m ON m.part_id = p.id
 WHERE p.sku LIKE 'CC-%'
 GROUP BY p.sku, b.quantity
 ORDER BY p.sku DESC
 LIMIT 20;
