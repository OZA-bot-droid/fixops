package com.fixops.inventory.stock.dto;

import java.time.Instant;

/**
 * 작업별 부품 사용 내역 한 줄 (D12, GET /inventory/usages).
 *
 * status : ACTIVE(사용됨) / CANCELLED(취소됨, 반납 기능이 생기면 쓰인다)
 * usedBy : 사용한 회원 번호. part_usage 에는 없어서 같은 멱등 키의 원장(stock_movement.created_by)에서 가져온다
 */
public record UsageHistoryResult(
        long usageId,
        long workOrderId,
        long partId,
        String sku,
        String partName,
        int quantity,
        String status,
        Long usedBy,
        Instant occurredAt) {}
