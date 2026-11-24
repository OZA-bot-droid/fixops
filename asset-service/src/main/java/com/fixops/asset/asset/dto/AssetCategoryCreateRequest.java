package com.fixops.asset.asset.dto;

import com.fixops.asset.common.enums.IndustryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 조직에 설비 카테고리를 추가하는 요청. */
public record AssetCategoryCreateRequest(
        @NotNull @Positive Long organizationId,
        @NotNull IndustryType industryType,
        @NotBlank @Size(max = 100) String name) {}
