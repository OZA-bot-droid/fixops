package com.fixops.workorder.workorder.domain;

public enum WorkOrderStatus {
    // 접수 / 배정 / 진행 / 완료 / 반려 / 취소
    REPORTED, ASSIGNED, IN_PROGRESS, COMPLETED, REJECTED, CANCELLED
}
