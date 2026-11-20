package com.fixops.asset.asset.dto;

import com.fixops.asset.asset.entity.AssetCategory;
import com.fixops.asset.common.enums.IndustryType;

/** 산업별 설비 분류 응답. */
public record AssetCategoryResponse(Long id, IndustryType industryType, String name) {
    public static AssetCategoryResponse from(AssetCategory category) {
        return new AssetCategoryResponse(category.getId(), category.getIndustryType(), category.getName());
    }
}
