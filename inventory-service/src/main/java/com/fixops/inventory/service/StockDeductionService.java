package com.fixops.inventory.service;

import com.fixops.inventory.dto.DeductCommand;
import com.fixops.inventory.dto.DeductResult;
import com.fixops.inventory.common.exception.IdempotencyKeyReuseException;
import com.fixops.inventory.common.exception.InsufficientStockException;
import com.fixops.inventory.common.exception.StockNotFoundException;

import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 재고 차감. 같은 규칙을 두 가지 동시성 방식으로 구현해 테스트로 비교한다.
 * 한 트랜잭션 안에서 (잔액 차감 + 부품 사용 기록 + 원장 기록)이 모두 성공하거나 모두 롤백된다.
 */
@Service
public class StockDeductionService {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public StockDeductionService(DataSource dataSource, PlatformTransactionManager txManager) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.tx = new TransactionTemplate(txManager);
    }

    /** 방식 1: SELECT ... FOR UPDATE 로 잔액 행을 잠근 뒤 확인하고 차감한다. */
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

    /** 방식 2: 조건부 UPDATE (WHERE quantity >= ?) 한 문장으로 확인과 차감을 동시에 한다. */
    public DeductResult deductConditional(DeductCommand c) {
        return tx.execute(status -> {
            // 사용 기록을 먼저 넣어 operationKey 중복을 UNIQUE 제약으로 막는다.
            List<Long> inserted = jdbc.queryForList(
                    "INSERT INTO part_usage (organization_id, work_order_id, part_id, quantity, operation_key) "
                            + "VALUES (?, ?, ?, ?, ?) ON CONFLICT (organization_id, operation_key) DO NOTHING "
                            + "RETURNING id",
                    Long.class, c.organizationId(), c.workOrderId(), c.partId(), c.quantity(), c.operationKey());
            if (inserted.isEmpty()) {
                DeductResult replay = replayIfExists(c);
                if (replay == null) {
                    throw new IllegalStateException("operationKey conflict but usage not found");
                }
                return replay;
            }
            List<Integer> remaining = jdbc.queryForList(
                    "UPDATE stock_balance SET quantity = quantity - ?, version = version + 1, updated_at = now() "
                            + "WHERE part_id = ? AND quantity >= ? RETURNING quantity",
                    Integer.class, c.quantity(), c.partId(), c.quantity());
            if (remaining.isEmpty()) {
                // 예외로 트랜잭션이 롤백되면서 위에서 넣은 사용 기록도 함께 사라진다.
                Integer exists = jdbc.queryForObject(
                        "SELECT count(*) FROM stock_balance WHERE part_id = ?", Integer.class, c.partId());
                if (exists == null || exists == 0) {
                    throw new StockNotFoundException("stock balance not found: partId=" + c.partId());
                }
                throw new InsufficientStockException(
                        "insufficient stock: partId=" + c.partId() + ", requested=" + c.quantity());
            }
            insertMovement(c);
            return new DeductResult(inserted.get(0), remaining.get(0), false);
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
