package com.fixops.inventory.stock.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 입고 요청 본문 (Controller 입구, OpenAPI ReceiptRequest).
 *   {"partId": 92, "quantity": 50, "operationKey": "receipt-92-a1b2c3", "note": "11월 정기 입고"}
 *
 * operationKey 는 화면이 입고 화면을 열 때 만들고, 재시도할 때 같은 값을 다시 보낸다.
 * Idempotency-Key 헤더와 같은 값이어야 한다 (D7 결정, Controller 에서 확인).
 */
public record ReceiptRequest(
        @NotNull @Positive Long partId,
        @NotNull @Min(1) Integer quantity,
        @NotBlank @Size(min = 8, max = 100) String operationKey,
        @Size(max = 200) String note) {}
