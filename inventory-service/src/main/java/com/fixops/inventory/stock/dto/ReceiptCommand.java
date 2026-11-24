package com.fixops.inventory.stock.dto;

/**
 * 입고에 필요한 값 (Service 입구).
 * organizationId, actorId 는 로그인 정보(헤더)에서, 나머지는 요청 본문에서 온다.
 */
public record ReceiptCommand(
        long organizationId,
        long partId,
        int quantity,
        String operationKey,
        long actorId,
        String note) {

    public ReceiptCommand {
        if (quantity <= 0) {
            throw new IllegalArgumentException("입고 수량은 1 이상이어야 합니다.");
        }
        if (operationKey == null || operationKey.isBlank()) {
            throw new IllegalArgumentException("operationKey는 필수입니다.");
        }
        // 빈 메모는 저장하지 않는다 (null 로)
        note = (note == null || note.isBlank()) ? null : note.trim();
    }
}
