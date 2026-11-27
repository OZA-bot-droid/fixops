package com.fixops.inventory.common.security;

import com.fixops.inventory.common.exception.DependencyUnavailableException;
import com.fixops.inventory.common.exception.auth.OrgAccessDeniedException;
import com.fixops.inventory.common.exception.auth.UnauthenticatedException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * "이 요청을 보낸 사람이 누구이고, 이 기능을 써도 되는가"를 확인하는 입구 (D13, 2026-11-27 변경).
 * Asset 서비스의 LocationAuthorization 과 같은 방식이다.
 *
 * 흐름
 *   화면 → Gateway(JWT·세션 확인) → Inventory Controller
 *     → 여기서 Member 에 물어본다: POST {member}/internal/authorization/check
 *         Authorization: 사용자 JWT 그대로
 *         X-FixOps-Service: inventory, X-FixOps-Service-Key: INVENTORY_AUTHZ_KEY   (Inventory 가 보낸 요청이라는 증명)
 *         본문 { organizationId, complexId }
 *     ← { memberId, membershipId, organizationId, role, allowed }
 *   → CurrentMember(memberId, role, organizationId) 를 돌려준다. Service 는 예전과 똑같이 쓴다.
 *
 * 왜 헤더(X-Member-Id 등)를 더 이상 믿지 않나
 *   헤더는 누구나 적어 보낼 수 있다("나는 ADMIN"). 그래서 Gateway 가 지운다(StripIdentityHeaders).
 *   JWT 는 Member 가 서명해서 위조할 수 없고, Member 에 다시 물어보면 "지금도" 그 조직의 구성원인지까지 확인된다
 *   (JWT 발급 뒤 조직에서 나간 경우를 막음).
 *
 * complexId
 *   BUILDING(아파트) 조직이면 필수, FACTORY 면 없어야 한다 (Member 가 판단).
 *   재고는 조직 단위라 complexId 는 권한 확인에만 쓰고 데이터를 거르는 데는 쓰지 않는다 (2026-11-27 확정).
 */
@Component
public class InventoryAuthorization {

    private static final Logger log = LoggerFactory.getLogger(InventoryAuthorization.class);
    private static final Set<String> READ_ROLES = Set.of("ADMIN", "TECHNICIAN");
    private static final Set<String> ADMIN_ROLES = Set.of("ADMIN");

    private final RestClient member;
    private final String serviceKey;

    public InventoryAuthorization(
            @Value("${fixops.member.url:http://127.0.0.1:8081}") String memberUrl,
            @Value("${fixops.internal.inventory-authz-key:}") String serviceKey) {
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.member = RestClient.builder().baseUrl(memberUrl).requestFactory(factory).build();
        this.serviceKey = serviceKey;
        if (serviceKey.length() < 43) {
            // 시작은 되게 두고, 요청이 오면 503 으로 알린다 (Asset 과 같음). 로그로 미리 알려 둔다
            log.warn("INVENTORY_AUTHZ_KEY 가 비어 있거나 짧습니다. 권한 확인 요청이 모두 503 이 됩니다.");
        }
    }

    /** 조회: ADMIN, TECHNICIAN */
    public CurrentMember requireRead(String bearer, Long organizationId, Long complexId) {
        return require(bearer, organizationId, complexId, READ_ROLES);
    }

    /**
     * 부품 사용(차감): ADMIN, TECHNICIAN (D8).
     * 여기서는 역할만 본다. TECHNICIAN 이 "자기에게 배정된 작업"인지는 WorkOrder 정보가 있어야 알 수 있어서
     * StockService.use() 에서 확인한다.
     */
    public CurrentMember requireUsage(String bearer, Long organizationId, Long complexId) {
        return require(bearer, organizationId, complexId, READ_ROLES);
    }

    /** 등록·수정·입고, 변동 이력, 요청 승인·반려: ADMIN 만 */
    public CurrentMember requireAdmin(String bearer, Long organizationId, Long complexId) {
        return require(bearer, organizationId, complexId, ADMIN_ROLES);
    }

    // Member 가 돌려주는 모양 {success, data:{...}}
    record Scope(Long organizationId, Long complexId) {}
    record Decision(Long memberId, Long membershipId, Long organizationId, String role, Boolean allowed) {}
    record Envelope(Boolean success, Decision data) {}

    private CurrentMember require(String bearer, Long organizationId, Long complexId, Set<String> roles) {
        if (bearer == null || !bearer.startsWith("Bearer ") || bearer.length() <= 7) {
            throw new UnauthenticatedException();                       // 401: 로그인 증명서 없음
        }
        if (organizationId == null) {
            throw new IllegalArgumentException("organizationId 가 필요합니다.");   // 400
        }
        if (serviceKey.length() < 43) {
            throw new DependencyUnavailableException("권한 확인 설정(INVENTORY_AUTHZ_KEY)이 없습니다.");   // 503
        }

        Envelope response;
        try {
            response = member.post().uri("/internal/authorization/check")
                    .header("Authorization", bearer)
                    .header("X-FixOps-Service", "inventory")
                    .header("X-FixOps-Service-Key", serviceKey)
                    .body(new Scope(organizationId, complexId))
                    .retrieve()
                    .body(Envelope.class);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 401) {
                throw new UnauthenticatedException();                   // 토큰 만료·위조
            }
            if (status == 403) {
                throw new OrgAccessDeniedException();                   // 그 조직·단지의 구성원이 아님
            }
            log.warn("Member 권한 확인 응답 오류 status={}", status);
            throw new DependencyUnavailableException("권한을 확인할 수 없습니다.");
        } catch (RestClientException e) {
            log.warn("Member 권한 확인 호출 실패: {}", e.getMessage());   // 연결 실패, 시간 초과
            throw new DependencyUnavailableException("권한을 확인할 수 없습니다.");
        }

        if (response == null || !Boolean.TRUE.equals(response.success()) || response.data() == null) {
            throw new DependencyUnavailableException("권한 확인 응답이 올바르지 않습니다.");
        }
        Decision d = response.data();
        // 허용됐는지, 요청한 조직과 같은 조직인지, 역할이 맞는지 직접 다시 본다 (Member 응답도 그대로 믿지 않는다)
        if (!Boolean.TRUE.equals(d.allowed()) || d.memberId() == null || d.memberId() <= 0
                || !organizationId.equals(d.organizationId()) || d.role() == null || !roles.contains(d.role())) {
            throw new OrgAccessDeniedException();
        }
        return new CurrentMember(d.memberId(), d.role(), d.organizationId());
    }
}
