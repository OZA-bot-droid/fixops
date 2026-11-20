package com.fixops.asset.asset.dto;

import com.fixops.asset.asset.entity.AssetStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 설비 설명/이름을 정정하거나 이력 보존을 위해 비활성화한다. */
public record AssetUpdateRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 2000) String description,
        @NotNull AssetStatus status) {}
