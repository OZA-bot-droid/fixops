package com.fixops.inventory.request.service;

import com.fixops.inventory.common.response.PageResult;
import com.fixops.inventory.part.entity.PartStatus;
import com.fixops.inventory.part.exception.DuplicateSkuException;
import com.fixops.inventory.part.exception.PartNotFoundException;
import com.fixops.inventory.request.dto.PartRequestCreateRequest;
import com.fixops.inventory.request.dto.PartRequestImageFile;
import com.fixops.inventory.request.dto.PartRequestImageInfo;
import com.fixops.inventory.request.dto.PartRequestResult;
import com.fixops.inventory.request.exception.PartRequestAlreadyDecidedException;
import com.fixops.inventory.request.exception.PartRequestNotFoundException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * 부품 요청 (D12~D14, 2026-11-27 팀장 승인). TECHNICIAN 이 요청하고 ADMIN 이 승인·반려한다.
 *
 * 재고와의 관계
 *   요청·승인·반려는 재고(stock_balance)와 원장(stock_movement)을 건드리지 않는다.
 *   승인은 "사 오겠다"는 기록일 뿐이고, 실제로 들어온 수량은 관리자가 입고로 따로 등록한다.
 *   그래야 원장에는 실제로 들어온 수량만 남는다.
 *
 * 동시성
 *   승인·반려는 "UPDATE ... WHERE status = 'REQUESTED'" 한 문장으로 처리한다.
 *   관리자 두 명이 동시에 눌러도 DB 가 한 줄씩 처리하므로 한 명만 바뀌고, 나머지는 0줄 → 409.
 *   (재고처럼 SELECT FOR UPDATE 로 먼저 잠글 필요가 없다. 확인과 변경이 한 문장 안에 있기 때문)
 */
@Service
public class PartRequestService {

    private static final Set<String> STATUSES = Set.of("REQUESTED", "APPROVED", "REJECTED");

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;            // 요청 + 사진을 한 번에 저장 (하나라도 실패하면 전부 취소)
    private final PartRequestImageStore images;      // 첨부 사진 (DB 저장)

    public PartRequestService(DataSource dataSource, PlatformTransactionManager txManager) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.tx = new TransactionTemplate(txManager);
        this.images = new PartRequestImageStore(jdbc);
    }

    /** 요청 등록 (사진 없이) */
    public PartRequestResult create(long organizationId, long memberId, PartRequestCreateRequest r) {
        return create(organizationId, memberId, r, List.of());
    }

    /**
     * 요청 등록. 등록된 부품이면 부품 정보로, 새 부품이면 보낸 값으로 저장한다.
     * 사진(1장)은 먼저 검사하고, 요청과 같은 트랜잭션으로 저장한다 → 사진 저장이 실패하면 요청도 남지 않는다
     */
    public PartRequestResult create(long organizationId, long memberId, PartRequestCreateRequest r, List<MultipartFile> files) {
        List<MultipartFile> photos = files == null ? List.of() : files.stream().filter(f -> f != null && !f.isEmpty()).toList();
        PartRequestImageStore.validate(photos);   // 잘못된 사진이면 아무것도 저장하기 전에 400
        String partName;
        String sku;
        String category;
        String unit;
        Integer safetyStock;

        if (r.partId() != null) {
            // 등록된 부품: 같은 조직의 부품인지 확인하고, 이름·SKU 등은 지금 부품 정보로 채운다 (요청 당시 모습을 남김)
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT sku, name, category, unit, safety_stock FROM part WHERE id = ? AND organization_id = ?",
                    r.partId(), organizationId);
            if (rows.isEmpty()) {
                throw new PartNotFoundException(r.partId());   // 없거나 다른 조직 부품 → 404
            }
            Map<String, Object> p = rows.get(0);
            partName = (String) p.get("name");
            sku = (String) p.get("sku");
            category = (String) p.get("category");
            unit = (String) p.get("unit");
            safetyStock = ((Number) p.get("safety_stock")).intValue();
        } else {
            // 새 부품: 부품명·SKU 필수. 이미 있는 SKU 면 "목록에서 고르라"는 뜻으로 409 (기존 부품 번호를 알려 준다)
            partName = requireText(r.partName(), "partName");
            sku = requireText(r.sku(), "sku");
            List<Map<String, Object>> dup = jdbc.queryForList(
                    "SELECT id, status FROM part WHERE organization_id = ? AND lower(sku) = lower(?)", organizationId, sku);
            if (!dup.isEmpty()) {
                throw new DuplicateSkuException(sku, ((Number) dup.get(0).get("id")).longValue(),
                        PartStatus.valueOf((String) dup.get(0).get("status")));
            }
            category = blankToNull(r.category());
            unit = blankToNull(r.unit());
            safetyStock = r.safetyStock() == null ? 0 : r.safetyStock();
        }

        String finalName = partName, finalSku = sku, finalCategory = category, finalUnit = unit;
        Integer finalSafety = safetyStock;
        PartRequestResult saved = tx.execute(status -> {
            PartRequestResult created = jdbc.queryForObject(
                    "INSERT INTO part_request (organization_id, part_id, part_name, sku, category, unit, safety_stock, "
                            + "quantity, work_order_id, reason, requested_by) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING *",
                    ROW, organizationId, r.partId(), finalName, finalSku, finalCategory, finalUnit, finalSafety,
                    r.quantity(), r.workOrderId(), blankToNull(r.reason()), memberId);
            images.save(organizationId, created.requestId(), photos);
            return created;
        });
        return attachImages(List.of(saved)).get(0);
    }

    /** 첨부 사진 한 장 (TECHNICIAN 은 onlyRequester 에 자기 번호를 넣어 자기 요청만) */
    public PartRequestImageFile image(long organizationId, long requestId, long imageId, Long onlyRequester) {
        return images.load(organizationId, requestId, imageId, onlyRequester);
    }

    // 결과들에 사진 정보를 붙인다 (한 번의 쿼리로)
    private List<PartRequestResult> attachImages(List<PartRequestResult> results) {
        Map<Long, List<PartRequestImageInfo>> byRequest = images.infos(results.stream().map(PartRequestResult::requestId).toList());
        return results.stream().map(x -> x.withImages(byRequest.getOrDefault(x.requestId(), List.of()))).toList();
    }

    /**
     * 요청 목록 (최신순).
     * @param requestedBy 주면 그 사람의 요청만 (TECHNICIAN 은 항상 자기 것만). null 이면 조직 전체 (ADMIN)
     * @param status      REQUESTED / APPROVED / REJECTED. null 이면 전체
     */
    public PageResult<PartRequestResult> list(long organizationId, Long requestedBy, String status, int page, int size) {
        if (status != null && !STATUSES.contains(status)) {
            throw new IllegalArgumentException("status 는 REQUESTED, APPROVED, REJECTED 중 하나여야 합니다.");
        }
        StringBuilder where = new StringBuilder(" FROM part_request WHERE organization_id = ?");
        List<Object> args = new ArrayList<>(List.of(organizationId));
        if (requestedBy != null) {
            where.append(" AND requested_by = ?");
            args.add(requestedBy);
        }
        if (status != null) {
            where.append(" AND status = ?");
            args.add(status);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + where, Long.class, args.toArray());

        args.add(size);
        args.add((long) page * size);
        List<PartRequestResult> content = jdbc.query(
                "SELECT *" + where + " ORDER BY requested_at DESC, id DESC LIMIT ? OFFSET ?", ROW, args.toArray());
        return new PageResult<>(attachImages(content), page, size, total == null ? 0 : total);
    }

    /** 승인 (ADMIN). 기록만 하고 재고는 바꾸지 않는다 */
    public PartRequestResult approve(long organizationId, long adminId, long requestId) {
        return decide(organizationId, adminId, requestId, "APPROVED", null);
    }

    /** 반려 (ADMIN). 사유 필수 */
    public PartRequestResult reject(long organizationId, long adminId, long requestId, String rejectReason) {
        return decide(organizationId, adminId, requestId, "REJECTED", requireText(rejectReason, "rejectReason"));
    }

    private PartRequestResult decide(long organizationId, long adminId, long requestId, String next, String rejectReason) {
        // 확인(REQUESTED 인가)과 변경을 한 문장으로. 바뀐 줄이 없으면 아래에서 이유를 구분한다
        List<PartRequestResult> updated = jdbc.query(
                "UPDATE part_request SET status = ?, reject_reason = ?, decided_by = ?, decided_at = now() "
                        + "WHERE id = ? AND organization_id = ? AND status = 'REQUESTED' RETURNING *",
                ROW, next, rejectReason, adminId, requestId, organizationId);
        if (!updated.isEmpty()) {
            return attachImages(updated).get(0);
        }
        List<String> current = jdbc.queryForList(
                "SELECT status FROM part_request WHERE id = ? AND organization_id = ?", String.class, requestId, organizationId);
        if (current.isEmpty()) {
            throw new PartRequestNotFoundException(requestId);                    // 없거나 다른 조직 → 404
        }
        throw new PartRequestAlreadyDecidedException(requestId, current.get(0));  // 이미 처리됨 → 409
    }

    // ---- 도우미 ----

    private static final RowMapper<PartRequestResult> ROW = (rs, i) -> new PartRequestResult(
            rs.getLong("id"), nullableLong(rs, "part_id"), rs.getString("part_name"), rs.getString("sku"),
            rs.getString("category"), rs.getString("unit"), nullableInt(rs, "safety_stock"), rs.getInt("quantity"),
            nullableLong(rs, "work_order_id"), rs.getString("reason"), rs.getString("status"),
            rs.getString("reject_reason"), rs.getLong("requested_by"), nullableLong(rs, "decided_by"),
            toInstant(rs.getObject("requested_at", OffsetDateTime.class)),
            toInstant(rs.getObject("decided_at", OffsetDateTime.class)), List.of());

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 값이 필요합니다.");
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long v = rs.getLong(column);
        return rs.wasNull() ? null : v;
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int v = rs.getInt(column);
        return rs.wasNull() ? null : v;
    }

    private static Instant toInstant(OffsetDateTime time) {
        return time == null ? null : time.toInstant();
    }
}
