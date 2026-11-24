package com.fixops.inventory.part.repository;

import com.fixops.inventory.part.entity.Part;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;


// part 테이블 접근 창구.
// 메서드 이름만 규칙대로 쓰면 Spring Data JPA가 SQL을 자동으로 만들어 준다.
public interface PartRepository extends JpaRepository<Part, Long> {

    // 같은 조직에 같은 SKU가 이미 있는지 (등록 전 중복 확인)
    boolean existsByOrganizationIdAndSku(Long organizationId, String sku);

    // 같은 조직 + SKU로 조회 (INACTIVE 부품 재활성화 안내에 사용)
    Optional<Part> findByOrganizationIdAndSku(Long organizationId, String sku);

    // 다른 조직의 부품은 못 보게: id와 조직을 함께 조건으로 단건 조회
    Optional<Part> findByIdAndOrganizationId(Long id, Long organizationId);
}
