package com.fixops.inventory.stock.dto;

/**
 * 차감 결과.
 * usageId           : 부품 사용 기록(part_usage) 번호
 * remainingQuantity : 차감 후 남은 수량
 * replayed=true 이면 같은 operationKey로 이미 처리된 요청의 결과를 다시 돌려준 것이다.
 */
public record DeductResult(long usageId, int remainingQuantity, boolean replayed) {}
