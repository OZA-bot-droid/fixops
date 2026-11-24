package com.fixops.asset.asset.repository;

import com.fixops.asset.asset.entity.AssetCategory;
import com.fixops.asset.common.enums.IndustryType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 조직 및 산업 유형별 AssetCategory를 조회한다. */
public interface AssetCategoryRepository extends JpaRepository<AssetCategory, Long> {
    /** 다른 조직의 카테고리가 섞이지 않게 조직과 산업 유형을 모두 조건으로 사용한다. */
    List<AssetCategory> findAllByOrganizationIdAndIndustryTypeOrderByName(
            Long organizationId, IndustryType industryType);
    Optional<AssetCategory> findByIdAndOrganizationId(Long id, Long organizationId);
    Optional<AssetCategory> findByIdAndOrganizationIdAndIndustryType(
            Long id, Long organizationId, IndustryType industryType);
    boolean existsByOrganizationIdAndIndustryTypeAndName(
            Long organizationId, IndustryType industryType, String name);
}
