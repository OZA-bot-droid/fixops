package com.fixops.inventory.stock.client;

import java.util.Optional;

/**
 * WorkOrder 서비스에 작업 정보를 물어보는 창구 (Inventory → WorkOrder).
 *
 * 왜 인터페이스인가
 *   실제 호출 방법(주소, 인증, REST/gRPC)은 아직 박천보님과 정하는 중이다.
 *   StockService 는 이 인터페이스만 알고, 실제 구현은 나중에 끼워 넣는다.
 *   그러면 연결 방법이 정해져도 StockService 는 고칠 필요가 없다.
 *
 * 구현체 (설정 fixops.workorder.fake 로 하나만 선택된다)
 *   - UnconnectedWorkOrderClient : 기본값. 아직 연결 안 됨 → 항상 503 (차감하지 않음)
 *   - FakeWorkOrderClient        : ⚠️ 가짜(FAKE). 로컬 개발·Postman 시험용. 운영에서 켜면 안 됨
 *   - (예정) 실제 HTTP/gRPC 구현  : 박천보님 API 확정 후 추가
 */
public interface WorkOrderClient {

    /**
     * 작업 정보를 가져온다.
     * @return 작업이 없으면 Optional.empty()
     * @throws com.fixops.inventory.common.exception.DependencyUnavailableException
     *         WorkOrder 서비스에 연결할 수 없을 때 (→ 503, 차감하지 않음. docs/06 "무조건 차감 금지")
     */
    Optional<WorkOrderInfo> findWorkOrder(long organizationId, long workOrderId);
}
