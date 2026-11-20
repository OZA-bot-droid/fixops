package com.fixops.asset.location.dto;

import com.fixops.asset.location.entity.LocationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 새 루트 또는 하위 위치를 만드는 요청 DTO. */
public record LocationCreateRequest(
        @NotNull @Positive Long organizationId,
        @Positive Long complexId,
        @Positive Long parentId,
        @NotNull LocationType locationType,
        @NotBlank @Size(max = 100) String code,
        @NotBlank @Size(max = 200) String name) {}
