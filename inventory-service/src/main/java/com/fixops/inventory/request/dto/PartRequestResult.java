package com.fixops.inventory.request.dto;

import java.time.Instant;
import java.util.List;

/**
 * 부품 요청 한 건.
 * status : REQUESTED(요청됨) / APPROVED(승인) / REJECTED(반려)
 * 승인은 기록만 한다. 재고는 관리자가 입고(POST /inventory/receipts)로 따로 늘린다 (2026-11-27 결정).
 * images: 첨부 사진 정보 (최대 1장, 요청할 때만 첨부). 사진 내용은 따로 받는다
 */
public record PartRequestResult(
        long requestId,
        Long partId,
        String partName,
        String sku,
        String category,
        String unit,
        Integer safetyStock,
        int quantity,
        Long workOrderId,
        String reason,
        String status,
        String rejectReason,
        long requestedBy,
        Long decidedBy,
        Instant requestedAt,
        Instant decidedAt,
        List<PartRequestImageInfo> images) {

    /** 사진 정보를 채운 새 결과 (record 는 값을 바꿀 수 없어서 새로 만든다) */
    public PartRequestResult withImages(List<PartRequestImageInfo> newImages) {
        return new PartRequestResult(requestId, partId, partName, sku, category, unit, safetyStock, quantity,
                workOrderId, reason, status, rejectReason, requestedBy, decidedBy, requestedAt, decidedAt, newImages);
    }
}
