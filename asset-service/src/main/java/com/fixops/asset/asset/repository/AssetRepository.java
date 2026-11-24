package com.fixops.asset.asset.repository;

import com.fixops.asset.asset.entity.Asset;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 조직·단지 범위에 한정해 설비를 조회하고 저장한다. */
public interface AssetRepository extends JpaRepository<Asset, Long> {
    List<Asset> findAllByOrganizationIdAndComplexIdOrderByIdDesc(Long organizationId, Long complexId);
    List<Asset> findAllByOrganizationIdAndComplexIdIsNullOrderByIdDesc(Long organizationId);
    Optional<Asset> findByIdAndOrganizationIdAndComplexId(Long id, Long organizationId, Long complexId);
    Optional<Asset> findByIdAndOrganizationIdAndComplexIdIsNull(Long id, Long organizationId);
    /** 조직 내 설비가 카테고리를 참조하는지 확인해 사용 중 삭제를 막는다. */
    boolean existsByCategory_IdAndOrganizationId(Long categoryId, Long organizationId);
}
