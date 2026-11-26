package com.fixops.inventory.dto;

/** replayed=true 이면 같은 operationKey로 이미 처리된 요청의 결과를 다시 돌려준 것이다. */
public record DeductResult(long usageId, int remainingQuantity, boolean replayed) {}
