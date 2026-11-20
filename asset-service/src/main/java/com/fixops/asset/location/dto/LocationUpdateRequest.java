package com.fixops.asset.location.dto;

import com.fixops.asset.location.entity.LocationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 위치 ID·유형·부모는 유지하고 코드·이름·상태만 변경한다. */
public record LocationUpdateRequest(
        @NotBlank @Size(max = 100) String code,
        @NotBlank @Size(max = 200) String name,
        @NotNull LocationStatus status) {}
