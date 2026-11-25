package com.fixops.inventory.stock.client;

import com.fixops.inventory.common.exception.DependencyUnavailableException;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 기본 구현: WorkOrder 와 아직 연결되지 않았음.
 *
 * 작업 상태를 확인할 수 없으면 차감하면 안 된다 (docs/06: 조회 실패 시 차감 금지, 503).
 * 그래서 항상 DependencyUnavailableException 을 던진다. "확인 못 했으니 일단 통과"는 절대 하지 않는다.
 *
 * 박천보님 API 가 정해지면 이 클래스 대신 실제 호출 구현을 만든다.
 * fixops.workorder.fake 가 true 가 아니면(기본) 이 클래스가 쓰인다.
 */
@Component
@ConditionalOnProperty(name = "fixops.workorder.fake", havingValue = "false", matchIfMissing = true)
public class UnconnectedWorkOrderClient implements WorkOrderClient {

    @Override
    public Optional<WorkOrderInfo> findWorkOrder(long organizationId, long workOrderId) {
        throw new DependencyUnavailableException("WorkOrder 서비스가 아직 연결되지 않았습니다.");
    }
}
