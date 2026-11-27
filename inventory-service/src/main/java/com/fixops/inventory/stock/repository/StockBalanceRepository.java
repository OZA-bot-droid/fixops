package com.fixops.inventory.stock.repository;

import com.fixops.inventory.stock.entity.StockBalance;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// stock_balance 테이블 접근 창구.
public interface StockBalanceRepository extends JpaRepository<StockBalance, Long> {

    @Query(value = """
            SELECT *
              FROM stock_balance
             WHERE part_id = :partId
            """,
            nativeQuery = true)
    Optional<StockBalance> findByPartId(@Param("partId") Long partId);

    /**
     * 부품 목록 조회 (부품 목록 화면 + MCP get_low_stock_parts / search_parts 에서 사용).
     *
     * [줄별 설명]
     *   SELECT p.id AS id, ... b.quantity AS quantity
     *       → 부품(part) 정보와 현재 수량(stock_balance)을 한 줄로 뽑는다.
     *         AS 뒤 이름이 PartStockRow 의 getter 이름과 같아야 값이 들어간다.
     *         "safetyStock" 처럼 큰따옴표를 붙인 이유: PostgreSQL 은 따옴표 없는 이름을 소문자로 바꾼다
     *         (safetyStock → safetystock). 따옴표를 붙여야 대문자가 그대로 남아 getter 와 맞는다.
     *
     *   FROM stock_balance b
     *   JOIN part p ON p.id = b.part_id
     *       → 재고 행과 부품 행을 part_id 로 짝지어 합친다.
     *         (JPQL의 join fetch b.part 와 같은 역할. SQL은 ON 으로 짝짓는 조건을 직접 적는다)
     *         한 번의 쿼리로 끝나므로 N+1 문제가 없다.
     *
     *   WHERE p.organization_id = :organizationId
     *       → 우리 단지 부품만. 다른 단지 부품이 보이면 안 되므로 항상 들어가는 조건.
     *
     *   AND p.status IN (:statuses)
     *       → 목록 안의 상태 중 하나면 통과.
     *         ACTIVE 선택 → ('ACTIVE'),  ALL 선택 → ('ACTIVE', 'INACTIVE')
     *         SQL에서는 DB에 저장된 글자 그대로 비교하므로 서비스에서 String 목록으로 넘긴다.
     *
     *   AND (p.sku ILIKE :keywordPattern OR p.name ILIKE :keywordPattern)
     *       → SKU 또는 이름에 검색어가 들어 있으면 통과.
     *         ILIKE : PostgreSQL 전용. 대소문자를 구분하지 않는 LIKE ('bolt' 로 'BOLT-M8' 검색 가능).
     *                 JPQL에서 lower(...) like lower(...) 로 쓰던 것을 한 단어로 줄였다.
     *         '%볼트%' : '볼트'가 어디든 들어 있으면 통과. 검색어가 없으면 서비스에서 '%' 하나만 넘긴다.
     *
     *   AND (:lowStockOnly = false OR b.quantity < p.safety_stock)
     *       → "부족 재고만 보기" 스위치.
     *         false(꺼짐) : 앞부분이 참 → OR 라서 전부 통과.
     *         true(켜짐)  : 수량 < 안전재고 인 것만 통과. 기준 "미만"(<)은 계약 문서와 같다.
     *
     * [countQuery]
     *   페이지 정보(전체 개수)를 계산하려고 같은 조건으로 개수만 센다.
     *
     */
    @Query(value = """
            SELECT p.id           AS id,
                   p.sku          AS sku,
                   p.name         AS name,
                   p.category     AS category,
                   p.unit         AS unit,
                   p.safety_stock AS "safetyStock",
                   p.status       AS status,
                   b.quantity     AS quantity,
                   p.created_at   AS "createdAt",
                   p.updated_at   AS "updatedAt",
                   EXISTS (SELECT 1 FROM part_image i WHERE i.part_id = p.id) AS "hasImage"
              FROM stock_balance b
              JOIN part p ON p.id = b.part_id
             WHERE p.organization_id = :organizationId
               AND p.status IN (:statuses)
               AND (p.sku ILIKE :keywordPattern OR p.name ILIKE :keywordPattern)
               AND (:lowStockOnly = false OR b.quantity < p.safety_stock)
            """,
            countQuery = """
            SELECT count(*)
              FROM stock_balance b
              JOIN part p ON p.id = b.part_id
             WHERE p.organization_id = :organizationId
               AND p.status IN (:statuses)
               AND (p.sku ILIKE :keywordPattern OR p.name ILIKE :keywordPattern)
               AND (:lowStockOnly = false OR b.quantity < p.safety_stock)
            """,
            nativeQuery = true)
    Page<PartStockRow> search(@Param("organizationId") Long organizationId,
                              @Param("statuses") Collection<String> statuses,
                              @Param("keywordPattern") String keywordPattern,
                              @Param("lowStockOnly") boolean lowStockOnly,
                              Pageable pageable);
}
