package com.fixops.asset.asset.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 조직 카테고리 이름을 변경하는 요청. 산업 유형은 생성 후 유지한다. */
public record AssetCategoryUpdateRequest(
        @NotBlank @Size(max = 100) String name) {}
