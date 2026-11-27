package com.fixops.inventory.stock.service;

import com.fixops.inventory.common.response.PageResult;
import com.fixops.inventory.stock.dto.MovementResult;
import com.fixops.inventory.stock.dto.UsageHistoryResult;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 재고 조회 전용 (D12). 읽기만 하고 아무것도 바꾸지 않는다.
 *   - 재고 변동 이력(원장) : GET /inventory/movements
 *   - 작업별 부품 사용 내역 : GET /inventory/usages
 *
 * 왜 StockService 와 나눴나
 *   StockService 는 잠금·트랜잭션으로 재고를 바꾸는 곳이다. 조회까지 넣으면 파일이 커지고,
 *   "여기는 재고를 바꾸는 코드"라는 경계가 흐려진다. 조회는 잠금이 필요 없어서 따로 둔다.
 *
 * 모든 쿼리는 organization_id 로 거른다. 다른 조직(단지)의 기록은 절대 보이지 않는다.
 */
@Service
public class StockQueryService {

    /** 허용하는 원장 구분 (V1 CHECK 제약과 같다) */
    private static final Set<String> MOVEMENT_TYPES = Set.of("RECEIPT", "USAGE", "STOCK_RETURN", "ADJUSTMENT");

    private final JdbcTemplate jdbc;

    public StockQueryService(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /**
     * 재고 변동 이력. 최신 기록이 먼저 온다.
     *
     * @param partId 주면 그 부품 하나만 (부품 목록에서 SKU·부품명을 눌렀을 때). null 이면 전체
     * @param type   RECEIPT / USAGE / STOCK_RETURN / ADJUSTMENT. null 이면 전체
     */
    public PageResult<MovementResult> movements(long organizationId, Long partId, String type, int page, int size) {
        if (type != null && !MOVEMENT_TYPES.contains(type)) {
            throw new IllegalArgumentException("type 은 RECEIPT, USAGE, STOCK_RETURN, ADJUSTMENT 중 하나여야 합니다.");
        }

        // 안쪽(m): 조직(+부품)의 원장 전체에 "그 시점 재고(balance_after)"를 붙인다.
        //   SUM(...) OVER (PARTITION BY part_id ORDER BY created_at, id)
        //   = 같은 부품의 기록을 시간 순으로 줄 세우고, 첫 줄부터 지금 줄까지 변동량을 더한 값
        // 바깥: 구분(type) 필터와 페이지는 계산이 끝난 뒤에 건다.
        //   (먼저 거르면 "입고만" 볼 때 사용분이 빠진 채로 더해져서 그 시점 재고가 틀어진다)
        StringBuilder inner = new StringBuilder(
                "SELECT m.id, m.part_id, m.movement_type, m.quantity_delta, m.work_order_id, m.created_by, m.note, m.created_at, "
                        + "SUM(m.quantity_delta) OVER (PARTITION BY m.part_id ORDER BY m.created_at, m.id) AS balance_after "
                        + "FROM stock_movement m WHERE m.organization_id = ?");
        List<Object> args = new ArrayList<>(List.of(organizationId));
        if (partId != null) {
            inner.append(" AND m.part_id = ?");
            args.add(partId);
        }
        String from = " FROM (" + inner + ") m JOIN part p ON p.id = m.part_id"
                + (type != null ? " WHERE m.movement_type = ?" : "");
        if (type != null) {
            args.add(type);
        }

        Long total = jdbc.queryForObject("SELECT COUNT(*)" + from, Long.class, args.toArray());

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((long) page * size);
        List<MovementResult> content = jdbc.query(
                "SELECT m.*, p.sku, p.name" + from + " ORDER BY m.created_at DESC, m.id DESC LIMIT ? OFFSET ?",
                (rs, i) -> new MovementResult(
                        rs.getLong("id"), rs.getLong("part_id"), rs.getString("sku"), rs.getString("name"),
                        rs.getString("movement_type"), rs.getInt("quantity_delta"), rs.getLong("balance_after"),
                        nullableLong(rs, "work_order_id"), nullableLong(rs, "created_by"), rs.getString("note"),
                        toInstant(rs.getObject("created_at", OffsetDateTime.class))),
                pageArgs.toArray());

        return new PageResult<>(content, page, size, total == null ? 0 : total);
    }

    /**
     * 작업별 부품 사용 내역. 최신 사용이 먼저 온다.
     *
     * @param workOrderId 주면 그 작업지시에서 쓴 부품만. null 이면 전체
     * @param partId      주면 그 부품을 쓴 기록만. null 이면 전체
     */
    public PageResult<UsageHistoryResult> usages(long organizationId, Long workOrderId, Long partId, int page, int size) {
        // 사용한 사람은 part_usage 에 없어서, 같은 조직·같은 멱등 키의 원장(USAGE)에서 created_by 를 가져온다.
        // (부품 사용 한 건 = part_usage 1행 + stock_movement 1행이 같은 operation_key 로 함께 저장된다, D8)
        StringBuilder from = new StringBuilder(
                " FROM part_usage u JOIN part p ON p.id = u.part_id "
                        + "LEFT JOIN stock_movement m ON m.organization_id = u.organization_id "
                        + "AND m.operation_key = u.operation_key AND m.movement_type = 'USAGE' "
                        + "WHERE u.organization_id = ?");
        List<Object> args = new ArrayList<>(List.of(organizationId));
        if (workOrderId != null) {
            from.append(" AND u.work_order_id = ?");
            args.add(workOrderId);
        }
        if (partId != null) {
            from.append(" AND u.part_id = ?");
            args.add(partId);
        }

        Long total = jdbc.queryForObject("SELECT COUNT(*)" + from, Long.class, args.toArray());

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((long) page * size);
        List<UsageHistoryResult> content = jdbc.query(
                "SELECT u.id, u.work_order_id, u.part_id, p.sku, p.name, u.quantity, u.status, m.created_by, u.created_at"
                        + from + " ORDER BY u.created_at DESC, u.id DESC LIMIT ? OFFSET ?",
                (rs, i) -> new UsageHistoryResult(
                        rs.getLong("id"), rs.getLong("work_order_id"), rs.getLong("part_id"), rs.getString("sku"),
                        rs.getString("name"), rs.getInt("quantity"), rs.getString("status"),
                        nullableLong(rs, "created_by"), toInstant(rs.getObject("created_at", OffsetDateTime.class))),
                pageArgs.toArray());

        return new PageResult<>(content, page, size, total == null ? 0 : total);
    }

    // DB 의 NULL 을 그대로 null 로 (getLong 은 NULL 을 0 으로 바꿔 버린다)
    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    // TIMESTAMPTZ → Instant(UTC). docs/04: 응답 시간은 UTC
    private static Instant toInstant(OffsetDateTime time) {
        return time == null ? null : time.toInstant();
    }
}
