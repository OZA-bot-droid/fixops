package com.fixops.inventory.stock.exception;

/**
 * 작업이 없거나 다른 단지의 작업일 때 (RESOURCE_NOT_FOUND, 404).
 * 다른 단지 작업도 "없음"으로 답한다 (부품과 같은 이유: 남의 작업 번호가 있는지 드러나지 않게).
 */
public class WorkOrderNotFoundException extends RuntimeException {
    public WorkOrderNotFoundException(long workOrderId) {
        super("작업을 찾을 수 없습니다: workOrderId=" + workOrderId);
    }
}
