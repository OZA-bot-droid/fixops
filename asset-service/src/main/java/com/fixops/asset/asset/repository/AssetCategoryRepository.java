package com.fixops.asset.asset.repository;

import com.fixops.asset.asset.entity.AssetCategory;
import com.fixops.asset.common.enums.IndustryType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 산업 유형별 AssetCategory를 조회한다. */
public interface AssetCategoryRepository extends JpaRepository<AssetCategory, Long> {
    List<AssetCategory> findAllByIndustryTypeOrderByName(IndustryType industryType);
    Optional<AssetCategory> findByIdAndIndustryType(Long id, IndustryType industryType);
}
