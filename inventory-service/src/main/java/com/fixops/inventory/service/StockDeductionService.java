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
 * 재고 차감. 동시 요청은 stock_balance 행을 SELECT ... FOR UPDATE 로 잠가 순차 처리한다(D5 결정).
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
