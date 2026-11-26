package com.fixops.inventory.stock.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fixops.inventory.common.exception.DependencyUnavailableException;
import com.fixops.inventory.common.exception.auth.OrgAccessDeniedException;
import com.fixops.inventory.common.exception.auth.UnauthenticatedException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * 실제 WorkOrder API 를 부르는 구현 (D11). 가짜를 끄면(기본) 이 클래스가 쓰인다.
 *
 * 부르는 API : GET {url}{base-path}/{workOrderId}   예) http://127.0.0.1:8083/work-orders/15
 *              직접 호출이면 base-path=/work-orders, Gateway 경유면 url=Gateway 주소, base-path=/api/work-orders
 * 보내는 헤더 : X-Member-Id (지금 WorkOrder 가 필수로 받음), Authorization (사용자 JWT, 있을 때만)
 * 받는 값    : 응답 data 의 id, organizationId, status, assignedTo 만 쓰고 나머지 필드는 무시한다
 *
 * 응답 → Inventory 결과 (docs/04 "외부 API 장애 시 무조건 성공 처리 금지")
 *   200                     → 작업 정보
 *   404                     → Optional.empty() (StockService 가 404 로 바꾼다)
 *   401                     → 401 UNAUTHENTICATED
 *   403                     → 403 ORG_ACCESS_DENIED
 *   400                     → 503 + 오류 로그 (우리가 요청을 잘못 보냈다는 뜻이라 사용자 잘못이 아니다)
 *   5xx · 시간 초과 · 연결 실패 → 503 DEPENDENCY_UNAVAILABLE (차감하지 않음)
 */
@Component
@ConditionalOnProperty(name = "fixops.workorder.fake", havingValue = "false", matchIfMissing = true)
public class HttpWorkOrderClient implements WorkOrderClient {

    private static final Logger log = LoggerFactory.getLogger(HttpWorkOrderClient.class);

    private final RestClient client;
    private final String basePath;

    /**
     * Asset 서비스(LocationAuthorization)와 같은 방식으로 RestClient 를 만든다.
     * 시간 제한도 Asset 과 같은 값(연결 2초, 응답 3초)을 기본으로 한다.
     */
    public HttpWorkOrderClient(
            @Value("${fixops.workorder.url:http://127.0.0.1:8083}") String url,
            @Value("${fixops.workorder.base-path:/work-orders}") String basePath,
            @Value("${fixops.workorder.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${fixops.workorder.read-timeout-ms:3000}") long readTimeoutMs) {
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(connectTimeoutMs)).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
        this.basePath = basePath;
    }

    // WorkOrder 응답 모양 {success, data:{...}}. 필요한 필드만 받고, 모르는 필드는 무시한다
    // (박천보님이 필드를 추가해도 우리 쪽이 깨지지 않게).
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Envelope(Boolean success, Data data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Data(Long id, Long organizationId, String status, Long assignedTo) {}

    @Override
    public Optional<WorkOrderInfo> findWorkOrder(long organizationId, long workOrderId, WorkOrderCaller caller) {
        Envelope body;
        try {
            body = client.get()
                    .uri(basePath + "/{id}", workOrderId)
                    .headers(h -> {
                        h.set("X-Member-Id", String.valueOf(caller.memberId()));
                        if (caller.authorization() != null && !caller.authorization().isBlank()) {
                            h.set("Authorization", caller.authorization());
                        }
                    })
                    .retrieve()
                    .body(Envelope.class);
        } catch (RestClientResponseException e) {          // WorkOrder 가 오류 상태 코드로 답한 경우
            int status = e.getStatusCode().value();
            if (status == 404) {
                return Optional.empty();
            }
            if (status == 401) {
                throw new UnauthenticatedException();
            }
            if (status == 403) {
                throw new OrgAccessDeniedException();
            }
            if (status == 400) {
                log.error("WorkOrder 가 400 을 돌려줌 (Inventory 의 요청 형식 문제). workOrderId={}, body={}",
                        workOrderId, e.getResponseBodyAsString());
            } else {
                log.warn("WorkOrder 응답 오류 status={} workOrderId={}", status, workOrderId);
            }
            throw new DependencyUnavailableException("WorkOrder 서비스 응답 오류 (" + status + ")");
        } catch (RestClientException e) {                  // 연결 실패, 시간 초과, 응답 해석 실패
            log.warn("WorkOrder 호출 실패 workOrderId={}: {}", workOrderId, e.getMessage());
            throw new DependencyUnavailableException("WorkOrder 서비스에 연결할 수 없습니다.");
        }

        // 200 인데 내용이 비었거나 꼭 필요한 값이 없으면, 믿을 수 없으니 차감하지 않는다 (503)
        if (body == null || !Boolean.TRUE.equals(body.success()) || body.data() == null
                || body.data().id() == null || body.data().organizationId() == null || body.data().status() == null) {
            log.warn("WorkOrder 응답에 필요한 값이 없음 workOrderId={}", workOrderId);
            throw new DependencyUnavailableException("WorkOrder 응답을 확인할 수 없습니다.");
        }

        Data d = body.data();
        // 이름 맞추기: WorkOrder 의 assignedTo → 우리 쪽 assignedTechnicianId
        return Optional.of(new WorkOrderInfo(d.id(), d.organizationId(), d.status(), d.assignedTo()));
    }
}
