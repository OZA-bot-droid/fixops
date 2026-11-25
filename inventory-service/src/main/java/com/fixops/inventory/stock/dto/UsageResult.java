package com.fixops.inventory.stock.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;

/**
 * 부품 사용(차감) 결과 (OpenAPI UsageResult). D8 에서 DeductResult 를 대신한다.
 *
 * usageId           : 부품 사용 기록(part_usage) 번호
 * remainingQuantity : 차감 후 남은 수량 (재시도 응답이면 지금 수량)
 * occurredAt        : 사용을 기록한 시각 (UTC)
 * replayed          : 같은 operationKey 로 이미 처리된 요청인지. JSON 에는 넣지 않고
 *                     Controller 가 201(새로 처리) / 200(재전달)을 고르는 데만 쓴다 (입고와 같은 방식).
 */
public record UsageResult(
        long usageId,
        long workOrderId,
        long partId,
        int quantity,
        int remainingQuantity,
        Instant occurredAt,
        @JsonIgnore boolean replayed) {}
