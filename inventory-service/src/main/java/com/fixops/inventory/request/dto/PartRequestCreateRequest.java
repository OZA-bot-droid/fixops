package com.fixops.inventory.request.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 부품 요청 등록 본문 (TECHNICIAN → ADMIN).
 *
 * 등록된 부품을 요청할 때 : partId 만 보내면 된다. 이름·SKU 등은 서버가 부품 정보에서 채운다
 * 목록에 없는 새 부품일 때: partId 없이 partName·sku(필수)와 category·unit·safetyStock 을 보낸다
 *                         → 관리자가 등록할 때 이 값이 그대로 채워진다
 */
public record PartRequestCreateRequest(
        @Positive Long partId,
        @Size(max = 200) String partName,
        @Size(max = 64) String sku,
        @Size(max = 64) String category,
        @Size(max = 16) String unit,
        @PositiveOrZero Integer safetyStock,
        @NotNull @Min(1) @Max(100000) Integer quantity,
        @Positive Long workOrderId,
        @Size(max = 500) String reason) {}
