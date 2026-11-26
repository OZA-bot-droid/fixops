package com.fixops.inventory.stock.client;

import java.util.Optional;

/**
 * WorkOrder 서비스에 작업 정보를 물어보는 창구 (Inventory → WorkOrder).
 *
 * 왜 인터페이스인가
 *   StockService 는 이 인터페이스만 알고, 실제로 어떻게 부르는지는 구현체가 맡는다.
 *   그래서 주소·인증 방식이 바뀌어도 StockService 는 고칠 필요가 없다.
 *
 * 구현체 (설정 fixops.workorder.fake 로 하나만 선택된다)
 *   - HttpWorkOrderClient : 기본값. 실제 WorkOrder API(GET /work-orders/{id})를 부른다 (D11)
 *   - FakeWorkOrderClient : ⚠️ 가짜(FAKE). 로컬 개발·Postman 시험용. 실제 연결 확인 후 삭제 예정
 */
public interface WorkOrderClient {

    /**
     * 작업 정보를 가져온다.
     * @param caller 요청한 사용자 (WorkOrder 에 "누가 묻는지" 전달, D11)
     * @return 작업이 없으면 Optional.empty()
     * @throws com.fixops.inventory.common.exception.DependencyUnavailableException
     *         WorkOrder 서비스에 연결할 수 없을 때 (→ 503, 차감하지 않음. docs/06 "무조건 차감 금지")
     */
    Optional<WorkOrderInfo> findWorkOrder(long organizationId, long workOrderId, WorkOrderCaller caller);
}
