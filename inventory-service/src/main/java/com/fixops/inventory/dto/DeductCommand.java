package com.fixops.inventory.dto;

public record DeductCommand(
        long organizationId,
        long workOrderId,
        long partId,
        int quantity,
        String operationKey,
        long actorId) {

    public DeductCommand {
        if (quantity <= 0) {
            throw new IllegalArgumentException("수량이 0보다 커야합니다.");
        }
        if (operationKey == null || operationKey.isBlank()) {
            throw new IllegalArgumentException("operationKey값이 필수적으로 필요하다");
        }
    }
}
