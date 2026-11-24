package com.fixops.inventory.dto.stock;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;

/**
 * 재고 변동 결과 (OpenAPI StockChangeResult). 입고는 quantityDelta 가 양수.
 *
 * replayed : 같은 operationKey 로 이미 처리된 요청을 다시 받은 것인지.
 *            응답 JSON 에는 넣지 않고(@JsonIgnore), Controller 가 201(새로 처리) / 200(재전달)을 고르는 데만 쓴다.
 */
public record StockChangeResult(
        long movementId,
        long partId,
        int quantityDelta,
        int remainingQuantity,
        Instant occurredAt,
        @JsonIgnore boolean replayed) {}
