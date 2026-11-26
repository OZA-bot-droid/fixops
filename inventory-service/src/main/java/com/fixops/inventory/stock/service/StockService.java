package com.fixops.inventory.stock.service;

import com.fixops.inventory.stock.dto.ReceiptRequest;
import com.fixops.inventory.stock.dto.StockChangeResult;
import com.fixops.inventory.stock.dto.UsageRequest;
import com.fixops.inventory.stock.dto.UsageResult;
import com.fixops.inventory.stock.client.WorkOrderCaller;
import com.fixops.inventory.stock.client.WorkOrderClient;
import com.fixops.inventory.stock.client.WorkOrderInfo;
import com.fixops.inventory.stock.exception.WorkOrderNotFoundException;
import com.fixops.inventory.stock.exception.WorkOrderNotInProgressException;
import com.fixops.inventory.common.exception.auth.OrgAccessDeniedException;
import com.fixops.inventory.common.security.CurrentMember;
import com.fixops.inventory.stock.exception.IdempotencyKeyReuseException;
import com.fixops.inventory.stock.exception.InsufficientStockException;
import com.fixops.inventory.part.exception.PartInactiveException;
import com.fixops.inventory.part.exception.PartNotFoundException;

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
    private final WorkOrderClient workOrders;   // 작업 상태 확인 창구 (D8). 실제/가짜 중 설정에 맞는 것이 들어온다

    public StockService(DataSource dataSource, PlatformTransactionManager txManager, WorkOrderClient workOrders) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.tx = new TransactionTemplate(txManager);
        this.workOrders = workOrders;
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
    public StockChangeResult receive(long organizationId, long actorId, ReceiptRequest r) {
        // 입력 정리 (Controller 의 @Valid 가 이미 검사했지만, 다른 곳에서 불려도 안전하게 한 번 더)
        if (r.quantity() == null || r.quantity() <= 0) {
            throw new IllegalArgumentException("입고 수량은 1 이상이어야 합니다.");
        }
        if (r.operationKey() == null || r.operationKey().isBlank()) {
            throw new IllegalArgumentException("operationKey는 필수입니다.");
        }
        long partId = r.partId();
        int quantity = r.quantity();
        String operationKey = r.operationKey();
        String note = (r.note() == null || r.note().isBlank()) ? null : r.note().trim();   // 빈 메모는 저장하지 않음

        return tx.execute(status -> {
            // 1) 잠금. part 와 JOIN 해서 단지와 상태를 함께 확인한다. FOR UPDATE OF b : 재고 행(b)만 잠근다.
            List<LockedStock> locked = jdbc.query(
                    "SELECT b.quantity, p.status FROM stock_balance b JOIN part p ON p.id = b.part_id "
                            + "WHERE b.part_id = ? AND p.organization_id = ? FOR UPDATE OF b",
                    (rs, i) -> new LockedStock(rs.getInt("quantity"), rs.getString("status")),
                    partId, organizationId);
            if (locked.isEmpty()) {
                throw new PartNotFoundException(partId);
            }

            // 2) 중복 확인
            StockChangeResult replay = replayReceiptIfExists(organizationId, partId, quantity, operationKey);
            if (replay != null) {
                return replay;
            }

            // 3) 상태 확인
            if (!"ACTIVE".equals(locked.get(0).status())) {
                throw new PartInactiveException(partId);
            }

            // 4) 증가
            jdbc.update("UPDATE stock_balance SET quantity = quantity + ?, version = version + 1, "
                    + "updated_at = now() WHERE part_id = ?", quantity, partId);

            // 5) 원장 기록. 같은 키를 다른 부품에 동시에 쓰면 잠금이 서로 달라 2)를 둘 다 통과할 수 있다.
            //    그때는 DB 의 UNIQUE(organization_id, operation_key) 가 막고, 그 오류를 409 로 바꾼다.
            try {
                return jdbc.queryForObject(
                        "INSERT INTO stock_movement (organization_id, part_id, movement_type, quantity_delta, "
                                + "operation_key, created_by, note) VALUES (?, ?, 'RECEIPT', ?, ?, ?, ?) "
                                + "RETURNING id, created_at",
                        (rs, i) -> new StockChangeResult(
                                rs.getLong("id"), partId, quantity,
                                locked.get(0).quantity() + quantity,
                                toInstant(rs.getObject("created_at", OffsetDateTime.class)),
                                false),
                        organizationId, partId, quantity, operationKey, actorId, note);
            } catch (DuplicateKeyException e) {
                throw new IdempotencyKeyReuseException("operationKey already used: " + operationKey);
            }
        });
    }

    /**
     * 같은 키의 원장이 있으면 그 결과를 돌려준다. 내용(종류, 부품, 수량)이 다르면 409.
     * remainingQuantity 는 지금 수량이다 (원장에는 그때의 잔액이 저장되어 있지 않다).
     */
    private StockChangeResult replayReceiptIfExists(long organizationId, long partId, int quantity,
                                                    String operationKey) {
        List<StockChangeResult> found = jdbc.query(
                "SELECT m.id, m.part_id, m.movement_type, m.quantity_delta, m.created_at, "
                        + "(SELECT quantity FROM stock_balance WHERE part_id = m.part_id) AS remaining "
                        + "FROM stock_movement m WHERE m.organization_id = ? AND m.operation_key = ?",
                (rs, i) -> {
                    boolean same = "RECEIPT".equals(rs.getString("movement_type"))
                            && rs.getLong("part_id") == partId
                            && rs.getInt("quantity_delta") == quantity;
                    if (!same) {
                        throw new IdempotencyKeyReuseException(
                                "operationKey already used with different content: " + operationKey);
                    }
                    return new StockChangeResult(rs.getLong("id"), partId, quantity,
                            rs.getInt("remaining"),
                            toInstant(rs.getObject("created_at", OffsetDateTime.class)), true);
                },
                organizationId, operationKey);
        return found.isEmpty() ? null : found.get(0);
    }

    // TIMESTAMPTZ 는 JDBC 에서 OffsetDateTime 으로 꺼내고, 응답은 Instant(UTC) 로 통일한다 (docs/04).
    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    /** 잠근 재고 행에서 꺼낸 값 (이 클래스 안에서만 쓰는 작은 묶음) */
    private record LockedStock(int quantity, String status) {}

    // ================= 부품 사용 = 차감 (D4~D5 틀, D8 완성) =================

    /**
     * 부품 사용 (POST /usages). 작업을 확인한 뒤 재고를 차감한다.
     *
     * 순서
     *   1) 입력 확인
     *   2) 같은 키로 이미 처리됐나?  → 그렇다면 WorkOrder 를 부르지 않고 처음 결과를 돌려준다 (200)
     *   3) WorkOrder 에 작업 확인    → 없거나 남의 단지 404, 배정 안 된 기사 403, 진행 중 아님 409, 장애 503
     *   4) 잠그고 차감               → deductWithLock (트랜잭션)
     *
     * 2)를 3)보다 먼저 하는 이유
     *   처음 요청이 차감까지 성공했는데 응답만 못 받아 재시도하는 경우, 그 사이 작업이 COMPLETED 가 될 수 있다.
     *   3)을 먼저 하면 이미 정상 처리된 요청을 "진행 중 아님"으로 거부하게 된다.
     *
     * 3)을 트랜잭션(4) 밖에서 하는 이유
     *   WorkOrder 호출은 네트워크라 느릴 수 있다. 재고 행을 잠근 채로 기다리면 같은 부품의 다른 요청이 모두 멈춘다.
     *   그래서 확인은 잠그기 전에 하고, 잠근 뒤에는 DB 일만 빠르게 끝낸다.
     *   (확인과 차감 사이에 작업 상태가 바뀔 수 있다. 그 경우는 docs/06 대로 차감을 유효한 사실로 두고 필요하면 보상 원장)
     */
    public UsageResult use(CurrentMember member, UsageRequest r, String authorization) {
        // 1) 입력 확인 (Controller 의 @Valid 가 이미 했지만, 다른 곳에서 불려도 안전하게)
        if (r.quantity() == null || r.quantity() <= 0) {
            throw new IllegalArgumentException("수량이 0보다 커야합니다.");
        }
        if (r.operationKey() == null || r.operationKey().isBlank()) {
            throw new IllegalArgumentException("operationKey값이 필수적으로 필요하다");
        }
        long organizationId = member.organizationId();

        // 2) 이미 처리된 요청인지 (잠그지 않고 먼저 한 번 본다. 진짜 확인은 4)에서 잠근 뒤 다시 한다)
        UsageResult replay = replayIfExists(organizationId, r);
        if (replay != null) {
            return replay;
        }

        // 3) 작업 확인
        //    authorization: 사용자가 보낸 JWT 를 WorkOrder 에 그대로 전달한다 (D11, "누가 묻는지")
        WorkOrderCaller caller = new WorkOrderCaller(authorization, member.memberId());
        WorkOrderInfo workOrder = workOrders.findWorkOrder(organizationId, r.workOrderId(), caller)
                .orElseThrow(() -> new WorkOrderNotFoundException(r.workOrderId()));
        if (workOrder.organizationId() != organizationId) {
            throw new WorkOrderNotFoundException(r.workOrderId());   // 남의 단지 작업도 "없음"
        }
        if (!member.isAdmin() && !Long.valueOf(member.memberId()).equals(workOrder.assignedTechnicianId())) {
            throw new OrgAccessDeniedException();                    // TECHNICIAN 은 자기 작업만 (ADMIN 은 단지 안 전체)
        }
        if (!workOrder.isInProgress()) {
            throw new WorkOrderNotInProgressException(r.workOrderId(), workOrder.status());
        }

        // 4) 차감
        return deductWithLock(organizationId, member.memberId(), r);
    }

    /**
     * 잔액 행을 잠근 뒤 중복 여부와 재고를 확인하고 차감한다. use() 에서만 부른다 (작업 확인을 건너뛰지 않게 private).
     *
     * 순서 : 잠금 → 중복 확인 → 재고 확인 → 차감 → 사용 기록(part_usage) → 원장(stock_movement)
     *
     * D8 수정: 잠글 때 part 와 JOIN 해서 organization_id 도 확인한다 (입고와 같은 방식).
     *          전에는 part_id 만 봐서, 다른 단지의 부품 번호를 넣으면 남의 재고를 차감할 수 있었다.
     */
    private UsageResult deductWithLock(long organizationId, long actorId, UsageRequest r) {
        return tx.execute(status -> {
            List<Integer> locked = jdbc.queryForList(
//  FOR UPDATE가 붙으면 이 행을 트랜잭션이 끝날 때까지 잠급니다. 같은 부품을 쓰려는 다른 요청은 여기서 줄을 섭니다.
                    "SELECT b.quantity FROM stock_balance b JOIN part p ON p.id = b.part_id "
                            + "WHERE b.part_id = ? AND p.organization_id = ? FOR UPDATE OF b",
                    Integer.class, r.partId(), organizationId);
            if (locked.isEmpty()) {
                throw new PartNotFoundException(r.partId());   // 없는 부품이거나 남의 단지 부품 (404)
            }
            // 잠금을 잡은 뒤에 중복 여부를 다시 확인하므로, 같은 키 동시 요청도 한 번만 차감된다.
            UsageResult replay = replayIfExists(organizationId, r);
            if (replay != null) {
                return replay;
            }
            // 재고 확인. 잠근 뒤에 읽은 값이라 그 사이에 다른 요청이 수량을 바꿀 수 없다.
            int current = locked.get(0);
            if (current < r.quantity()) {
                throw new InsufficientStockException(
                        "insufficient stock: partId=" + r.partId() + ", requested=" + r.quantity());
            }
            // 차감. version 도 함께 올려서, JPA 로 같은 행을 수정하던 쪽이 충돌을 알아채게 한다.
            jdbc.update("UPDATE stock_balance SET quantity = quantity - ?, version = version + 1, "
                    + "updated_at = now() WHERE part_id = ?", r.quantity(), r.partId());
            // 어느 작업에서 썼는지(part_usage)와 재고 거래 내역(stock_movement)을 둘 다 남긴다.
            // 같은 키를 다른 부품에 동시에 쓰면 잠금이 달라 위 중복 확인을 둘 다 통과할 수 있다.
            // 그때는 DB 의 UNIQUE(organization_id, operation_key) 가 막고, 그 오류를 409 로 바꾼다 (입고와 같음).
            try {
                UsageResult saved = insertUsage(organizationId, r, current - r.quantity());
                insertMovement(organizationId, actorId, r);
                return saved;
            } catch (DuplicateKeyException e) {
                throw new IdempotencyKeyReuseException("operationKey already used: " + r.operationKey());
            }
        });
    }

    /**
     * 같은 키의 사용 기록이 있으면 그 결과를 돌려준다. 부품·수량·작업 중 하나라도 다르면 409.
     * remaining 은 지금 수량이다 (기록에 그때의 잔액이 저장되어 있지 않다).
     */
    private UsageResult replayIfExists(long organizationId, UsageRequest r) {
        List<UsageResult> found = jdbc.query(
                "SELECT u.id, u.part_id, u.quantity, u.work_order_id, u.created_at, "
                        + "(SELECT quantity FROM stock_balance WHERE part_id = u.part_id) AS remaining "
                        + "FROM part_usage u WHERE u.organization_id = ? AND u.operation_key = ?",
                (rs, i) -> {
                    boolean same = rs.getLong("part_id") == r.partId()
                            && rs.getInt("quantity") == r.quantity()
                            && rs.getLong("work_order_id") == r.workOrderId();
                    if (!same) {
                        throw new IdempotencyKeyReuseException(
                                "operationKey already used with different content: " + r.operationKey());
                    }
                    return new UsageResult(rs.getLong("id"), r.workOrderId(), r.partId(), r.quantity(),
                            rs.getInt("remaining"),
                            toInstant(rs.getObject("created_at", OffsetDateTime.class)), true);
                },
                organizationId, r.operationKey());
        return found.isEmpty() ? null : found.get(0);
    }

    // 작업별 부품 사용 기록. RETURNING: INSERT 하면서 새로 붙은 번호와 기록 시각을 바로 돌려받는다.
    private UsageResult insertUsage(long organizationId, UsageRequest r, int remaining) {
        return jdbc.queryForObject(
                "INSERT INTO part_usage (organization_id, work_order_id, part_id, quantity, operation_key) "
                        + "VALUES (?, ?, ?, ?, ?) RETURNING id, created_at",
                (rs, i) -> new UsageResult(rs.getLong("id"), r.workOrderId(), r.partId(), r.quantity(), remaining,
                        toInstant(rs.getObject("created_at", OffsetDateTime.class)), false),
                organizationId, r.workOrderId(), r.partId(), r.quantity(), r.operationKey());
    }

    // 원장 기록. 사용은 재고가 줄어드는 것이므로 quantity_delta 를 음수(-quantity)로 적는다.
    private void insertMovement(long organizationId, long actorId, UsageRequest r) {
        jdbc.update("INSERT INTO stock_movement (organization_id, part_id, movement_type, quantity_delta, "
                        + "work_order_id, operation_key, created_by) VALUES (?, ?, 'USAGE', ?, ?, ?, ?)",
                organizationId, r.partId(), -r.quantity(), r.workOrderId(), r.operationKey(), actorId);
    }
}
