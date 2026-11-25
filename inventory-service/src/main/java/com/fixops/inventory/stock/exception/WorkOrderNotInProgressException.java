package com.fixops.inventory.stock.exception;

/**
 * 작업이 진행 중(IN_PROGRESS)이 아니라 부품을 쓸 수 없을 때 (WORK_ORDER_NOT_IN_PROGRESS, 409).
 * 예) 아직 배정만 된 작업(ASSIGNED), 이미 끝난 작업(COMPLETED), 취소된 작업(CANCELLED).
 */
public class WorkOrderNotInProgressException extends RuntimeException {
    public WorkOrderNotInProgressException(long workOrderId, String status) {
        super("진행 중인 작업이 아닙니다: workOrderId=" + workOrderId + ", status=" + status);
    }
}
