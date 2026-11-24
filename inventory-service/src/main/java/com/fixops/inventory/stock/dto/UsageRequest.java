package com.fixops.inventory.stock.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 부품 사용(재고 차감) 요청 본문 (OpenAPI UsageRequest).
 *   {"workOrderId": 811, "partId": 92, "quantity": 2, "operationKey": "usage-811-92-a"}
 *
 * 아직 이 요청을 받는 API(POST /usages)는 없다 (D8 예정). 지금은 StockService.deductWithLock 의 입력으로만 쓴다.
 * organizationId, actorId 는 요청이 아니라 로그인 정보에서 따로 넘긴다.
 */
public record UsageRequest(
        @NotNull @Positive Long workOrderId,
        @NotNull @Positive Long partId,
        @NotNull @Min(1) Integer quantity,
        @NotBlank @Size(min = 8, max = 100) String operationKey) {}
