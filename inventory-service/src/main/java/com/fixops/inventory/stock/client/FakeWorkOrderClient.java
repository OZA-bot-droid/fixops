package com.fixops.inventory.stock.client;

import com.fixops.inventory.common.exception.DependencyUnavailableException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * ⚠️⚠️⚠️ 가짜(FAKE) 구현 — 로컬 개발·Postman 시험 전용. 운영·통합 환경에서 켜면 안 된다. ⚠️⚠️⚠️
 *
 * WorkOrder 서비스를 실제로 부르지 않고, 정해진 규칙대로 "있는 척" 대답한다.
 * 박천보님 API 가 연결되기 전에 부품 사용(차감) 흐름을 시험해 보려고 만들었다.
 *
 * 켜는 법   : application.yml 의 fixops.workorder.fake=true (또는 환경 변수 WORKORDER_FAKE=true)
 * 기본값    : false (꺼짐). 꺼져 있으면 UnconnectedWorkOrderClient 가 쓰여 항상 503 이 된다.
 *
 * 가짜 응답 규칙 (Postman "5. 부품 사용" 폴더가 이 규칙을 쓴다)
 *   workOrderId 9001 → 상태 COMPLETED (부품 사용 불가 → 409 시험용)
 *   workOrderId 9004 → 작업 없음 (→ 404 시험용)
 *   workOrderId 9503 → WorkOrder 장애 (→ 503 시험용)
 *   그 밖의 번호     → 상태 IN_PROGRESS, 요청한 단지 소속, 배정 기사 = fixops.workorder.fake-technician-id (기본 1)
 *
 * TODO: 실제 WorkOrder 연결이 끝나면 이 클래스는 삭제한다.
 */
@Component
@ConditionalOnProperty(name = "fixops.workorder.fake", havingValue = "true")
public class FakeWorkOrderClient implements WorkOrderClient {

    private static final Logger log = LoggerFactory.getLogger(FakeWorkOrderClient.class);

    private final long fakeTechnicianId;

    public FakeWorkOrderClient(@Value("${fixops.workorder.fake-technician-id:1}") long fakeTechnicianId) {
        this.fakeTechnicianId = fakeTechnicianId;
        // 켜져 있다는 사실을 시작할 때 눈에 띄게 남긴다. 실수로 켠 채 배포하는 것을 막기 위해.
        log.warn("⚠️ 가짜 WorkOrderClient 사용 중 (fixops.workorder.fake=true). 개발 전용이며 실제 작업 상태를 확인하지 않습니다.");
    }

    @Override
    public Optional<WorkOrderInfo> findWorkOrder(long organizationId, long workOrderId) {
        // ⚠️ 가짜 규칙: 아래 번호들은 실패 경우를 시험하기 위한 약속된 값이다.
        if (workOrderId == 9004L) {
            return Optional.empty();
        }
        if (workOrderId == 9503L) {
            throw new DependencyUnavailableException("[가짜] WorkOrder 장애 시험");
        }
        String status = (workOrderId == 9001L) ? "COMPLETED" : "IN_PROGRESS";
        // ⚠️ 가짜: 실제로는 WorkOrder 가 알려 주는 단지·배정 기사다. 여기서는 요청한 단지와 설정값을 그대로 돌려준다.
        return Optional.of(new WorkOrderInfo(workOrderId, organizationId, status, fakeTechnicianId));
    }
}
