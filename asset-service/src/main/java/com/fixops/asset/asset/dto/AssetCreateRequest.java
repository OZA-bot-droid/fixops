package com.fixops.asset.asset.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 설비와 설비가 속한 조직·단지·위치·분류를 지정하는 생성 요청. */
public record AssetCreateRequest(
        @NotNull @Positive Long organizationId,
        @Positive Long complexId,
        @NotNull @Positive Long locationId,
        @NotNull @Positive Long categoryId,
        @NotBlank @Size(max = 100) String assetCode,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 2000) String description) {}
