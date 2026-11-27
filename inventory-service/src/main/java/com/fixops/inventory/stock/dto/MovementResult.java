package com.fixops.inventory.stock.dto;

import java.time.Instant;

/**
 * 재고 변동 이력(원장) 한 줄 (D12, GET /inventory/movements).
 *
 * movementType : RECEIPT(입고) / USAGE(사용) / STOCK_RETURN(반납) / ADJUSTMENT(조정)
 * quantityDelta: +면 늘어남, -면 줄어듦
 * balanceAfter : 이 기록 직후의 재고. 원장에 따로 저장하지 않고, 원장을 시간 순으로 더해서 계산한다
 *                (재고 = 원장의 합 이라는 D10 규칙이 지켜지므로 계산 결과가 실제 재고와 같다)
 * workOrderId  : 사용일 때 어느 작업지시인지 (입고면 null)
 * actorId      : 처리한 회원 번호 (이름은 Member 서비스 소유라 여기서는 번호만)
 */
public record MovementResult(
        long movementId,
        long partId,
        String sku,
        String partName,
        String movementType,
        int quantityDelta,
        long balanceAfter,
        Long workOrderId,
        Long actorId,
        String note,
        Instant occurredAt) {}
