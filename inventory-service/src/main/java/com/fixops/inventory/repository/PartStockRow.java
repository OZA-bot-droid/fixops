package com.fixops.inventory.repository;

import java.time.Instant;

/**
 * SQL 목록 조회 결과 한 줄을 받는 그릇 (인터페이스 프로젝션).
 *
 * [왜 필요한가]
 *   JPQL은 결과가 Entity 객체(StockBalance, Part)로 나왔지만,
 *   SQL(native query)은 결과가 "컬럼 값이 늘어선 행"으로 나온다.
 *   그 행을 담을 모양을 여기서 정한다. 구현 클래스는 Spring Data JPA가 자동으로 만든다.
 *
 * [연결 규칙]
 *   SQL의 별칭(AS 뒤 이름)과 getter 이름이 같아야 값이 들어간다.
 *     SELECT p.safety_stock AS safetyStock  →  getSafetyStock()
 */
public interface PartStockRow {
    Long getId();
    String getSku();
    String getName();
    String getCategory();
    String getUnit();
    Integer getSafetyStock();
    String getStatus();        // DB에는 'ACTIVE' 같은 글자로 저장되어 있어서 String으로 받는다
    Integer getQuantity();
    Instant getCreatedAt();
    Instant getUpdatedAt();
}
