package com.fixops.inventory.stock.client;

/**
 * WorkOrder 서비스에서 받아 오는 작업 정보 중, 부품 사용 확인에 필요한 것만.
 *
 * organizationId       : 작업이 속한 단지. 요청한 사람의 단지와 같아야 한다 (다르면 404)
 * status               : REPORTED / ASSIGNED / IN_PROGRESS / COMPLETED / REJECTED / CANCELLED (docs/04)
 *                        IN_PROGRESS 일 때만 부품을 쓸 수 있다 (D8 결정)
 * assignedTechnicianId : 배정된 기사. TECHNICIAN 은 자기에게 배정된 작업에만 부품을 쓸 수 있다 (없으면 null)
 *
 * 실제 응답 필드 이름은 박천보님과 확정 후 맞춘다.
 */
public record WorkOrderInfo(
        long workOrderId,
        long organizationId,
        String status,
        Long assignedTechnicianId) {

    public boolean isInProgress() {
        return "IN_PROGRESS".equals(status);
    }
}
