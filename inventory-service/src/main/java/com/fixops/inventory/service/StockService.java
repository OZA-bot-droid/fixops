package com.fixops.inventory.service;

import com.fixops.inventory.dto.DeductCommand;
import com.fixops.inventory.dto.DeductResult;
import com.fixops.inventory.dto.ReceiptCommand;
import com.fixops.inventory.dto.StockChangeResult;
import com.fixops.inventory.common.exception.IdempotencyKeyReuseException;
import com.fixops.inventory.common.exception.InsufficientStockException;
import com.fixops.inventory.common.exception.PartInactiveException;
import com.fixops.inventory.common.exception.PartNotFoundException;
import com.fixops.inventory.common.exception.StockNotFoundException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 재고 수량을 바꾸는 곳 (입고, 차감). 수량은 여기서만 바뀐다.
 * (D7: StockDeductionService 에 입고를 합치면서 StockService 로 이름 변경)
 *
 * 공통 원칙
 *   - 동시 요청은 stock_balance 행을 SELECT ... FOR UPDATE 로 잠가 순차 처리한다 (D5 결정).
 *   - 한 트랜잭션 안에서 (잔액 변경 + 원장 기록)이 모두 성공하거나 모두 롤백된다.
 *   - 같은 operationKey 로 다시 오면 새로 처리하지 않고 처음 결과를 돌려준다 (멱등성).
 *     같은 키인데 내용이 다르면 IdempotencyKeyReuseException (409).
 */
@Service
public class StockService {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public StockService(DataSource dataSource, PlatformTransactionManager txManager) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.tx = new TransactionTemplate(txManager);
    }

    // ================= 입고 (D7) =================

    /**
     * 입고. 재고를 늘리고 원장(stock_movement)에 RECEIPT 행을 남긴다.
     *
     * 순서 (차감과 같은 틀)
     *   1) 잠금   : 우리 단지 부품의 재고 행을 FOR UPDATE 로 잠근다. 없거나 남의 단지면 404.
     *   2) 중복   : 같은 operationKey 가 이미 있으면 처음 결과를 돌려준다 (replayed = true → 200).
     *   3) 상태   : 비활성(INACTIVE) 부품이면 409 (D7 결정: 비활성 부품은 입고 불가).
     *   4) 증가   : stock_balance.quantity 를 늘린다.
     *   5) 원장   : stock_movement 에 +quantity 로 기록한다 (note 포함).
     *
     * 2)를 1) 뒤에 하는 이유: 같은 키로 동시에 두 요청이 와도 잠금에서 줄을 서므로,
     * 뒤의 요청은 앞 요청이 남긴 원장을 보고 "이미 처리됨"으로 돌아간다. 입고가 두 번 되지 않는다.
     */
    public StockChangeResult receive(ReceiptCommand c) {
        return tx.execute(status -> {
            // 1) 잠금. part 와 JOIN 해서 단지와 상태를 함께 확인한다. FOR UPDATE OF b : 재고 행(b)만 잠근다.
            List<LockedStock> locked = jdbc.query(
                    "SELECT b.quantity, p.status FROM stock_balance b JOIN part p ON p.id = b.part_id "
                            + "WHERE b.part_id = ? AND p.organization_id = ? FOR UPDATE OF b",
                    (rs, i) -> new LockedStock(rs.getInt("quantity"), rs.getString("status")),
                    c.partId(), c.organizationId());
            if (locked.isEmpty()) {
                throw new PartNotFoundException(c.partId());
            }

            // 2) 중복 확인
            StockChangeResult replay = replayReceiptIfExists(c);
            if (replay != null) {
                return replay;
            }

            // 3) 상태 확인
            if (!"ACTIVE".equals(locked.get(0).status())) {
                throw new PartInactiveException(c.partId());
            }

            // 4) 증가
            jdbc.update("UPDATE stock_balance SET quantity = quantity + ?, version = version + 1, "
                    + "updated_at = now() WHERE part_id = ?", c.quantity(), c.partId());

            // 5) 원장 기록. 같은 키를 다른 부품에 동시에 쓰면 잠금이 서로 달라 2)를 둘 다 통과할 수 있다.
            //    그때는 DB 의 UNIQUE(organization_id, operation_key) 가 막고, 그 오류를 409 로 바꾼다.
            try {
                return jdbc.queryForObject(
                        "INSERT INTO stock_movement (organization_id, part_id, movement_type, quantity_delta, "
                                + "operation_key, created_by, note) VALUES (?, ?, 'RECEIPT', ?, ?, ?, ?) "
                                + "RETURNING id, created_at",
                        (rs, i) -> new StockChangeResult(
                                rs.getLong("id"), c.partId(), c.quantity(),
                                locked.get(0).quantity() + c.quantity(),
                                toInstant(rs.getObject("created_at", OffsetDateTime.class)),
                                false),
                        c.organizationId(), c.partId(), c.quantity(), c.operationKey(), c.actorId(), c.note());
            } catch (DuplicateKeyException e) {
                throw new IdempotencyKeyReuseException("operationKey already used: " + c.operationKey());
            }
        });
    }

    /**
     * 같은 키의 원장이 있으면 그 결과를 돌려준다. 내용(종류, 부품, 수량)이 다르면 409.
     * remainingQuantity 는 지금 수량이다 (원장에는 그때의 잔액이 저장되어 있지 않다).
     */
    private StockChangeResult replayReceiptIfExists(ReceiptCommand c) {
        List<StockChangeResult> found = jdbc.query(
                "SELECT m.id, m.part_id, m.movement_type, m.quantity_delta, m.created_at, "
                        + "(SELECT quantity FROM stock_balance WHERE part_id = m.part_id) AS remaining "
                        + "FROM stock_movement m WHERE m.organization_id = ? AND m.operation_key = ?",
                (rs, i) -> {
                    boolean same = "RECEIPT".equals(rs.getString("movement_type"))
                            && rs.getLong("part_id") == c.partId()
                            && rs.getInt("quantity_delta") == c.quantity();
                    if (!same) {
                        throw new IdempotencyKeyReuseException(
                                "operationKey already used with different content: " + c.operationKey());
                    }
                    return new StockChangeResult(rs.getLong("id"), c.partId(), c.quantity(),
                            rs.getInt("remaining"),
                            toInstant(rs.getObject("created_at", OffsetDateTime.class)), true);
                },
                c.organizationId(), c.operationKey());
        return found.isEmpty() ? null : found.get(0);
    }

    // TIMESTAMPTZ 는 JDBC 에서 OffsetDateTime 으로 꺼내고, 응답은 Instant(UTC) 로 통일한다 (docs/04).
    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    /** 잠근 재고 행에서 꺼낸 값 (이 클래스 안에서만 쓰는 작은 묶음) */
    private record LockedStock(int quantity, String status) {}

    // ================= 차감 (D4~D5) =================

    /** 잔액 행을 잠근 뒤 중복 여부와 재고를 확인하고 차감한다. */
    public DeductResult deductWithLock(DeductCommand c) {
        return tx.execute(status -> {
            List<Integer> locked = jdbc.queryForList(
//  FOR UPDATE가 붙으면 이 행을 트랜잭션이 끝날 때까지 잠급니다. 같은 부품을 쓰려는 다른 요청은 여기서 줄을 섭니다.
                    "SELECT quantity FROM stock_balance WHERE part_id = ? FOR UPDATE", Integer.class, c.partId());
            if (locked.isEmpty()) {
                throw new StockNotFoundException("stock balance not found: partId=" + c.partId());
            }
            // 잠금을 잡은 뒤에 중복 여부를 확인하므로, 같은 키 동시 요청도 한 번만 차감된다.
            DeductResult replay = replayIfExists(c);
            if (replay != null) {
                return replay;
            }
            int current = locked.get(0);
            if (current < c.quantity()) {
                throw new InsufficientStockException(
                        "insufficient stock: partId=" + c.partId() + ", requested=" + c.quantity());
            }
            jdbc.update("UPDATE stock_balance SET quantity = quantity - ?, version = version + 1, "
                    + "updated_at = now() WHERE part_id = ?", c.quantity(), c.partId());
            long usageId = insertUsage(c);
            insertMovement(c);
            return new DeductResult(usageId, current - c.quantity(), false);
        });
    }

    private DeductResult replayIfExists(DeductCommand c) {
        List<DeductResult> found = jdbc.query(
                "SELECT u.id, u.part_id, u.quantity, u.work_order_id, "
                        + "(SELECT quantity FROM stock_balance WHERE part_id = u.part_id) AS remaining "
                        + "FROM part_usage u WHERE u.organization_id = ? AND u.operation_key = ?",
                (rs, i) -> {
                    boolean same = rs.getLong("part_id") == c.partId()
                            && rs.getInt("quantity") == c.quantity()
                            && rs.getLong("work_order_id") == c.workOrderId();
                    if (!same) {
                        throw new IdempotencyKeyReuseException(
                                "operationKey already used with different content: " + c.operationKey());
                    }
                    return new DeductResult(rs.getLong("id"), rs.getInt("remaining"), true);
                },
                c.organizationId(), c.operationKey());
        return found.isEmpty() ? null : found.get(0);
    }

    private long insertUsage(DeductCommand c) {
        Long id = jdbc.queryForObject(
                "INSERT INTO part_usage (organization_id, work_order_id, part_id, quantity, operation_key) "
                        + "VALUES (?, ?, ?, ?, ?) RETURNING id",
                Long.class, c.organizationId(), c.workOrderId(), c.partId(), c.quantity(), c.operationKey());
        return id;
    }

    private void insertMovement(DeductCommand c) {
        jdbc.update("INSERT INTO stock_movement (organization_id, part_id, movement_type, quantity_delta, "
                        + "work_order_id, operation_key, created_by) VALUES (?, ?, 'USAGE', ?, ?, ?, ?)",
                c.organizationId(), c.partId(), -c.quantity(), c.workOrderId(), c.operationKey(), c.actorId());
    }
}
