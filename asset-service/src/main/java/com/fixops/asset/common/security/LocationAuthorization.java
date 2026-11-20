package com.fixops.asset.common.security;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
/** Gateway가 인증 후 전달한 사용자 ID·역할·조직 범위로 Location 접근 권한을 확인한다. */
public class LocationAuthorization {
    public void requireRead(Long memberId, String role, Long memberOrganizationId, Long requestOrganizationId) {
        requireMembership(memberId, role, memberOrganizationId, requestOrganizationId,
                List.of("ADMIN", "TECHNICIAN"));
    }

    public void requireWrite(Long memberId, String role, Long memberOrganizationId, Long requestOrganizationId) {
        requireMembership(memberId, role, memberOrganizationId, requestOrganizationId, List.of("ADMIN"));
    }

    // 외부에서 직접 전달된 헤더는 신뢰하면 안 된다. Gateway의 인증·헤더 재생성이 먼저 연결되어야 한다.
    private void requireMembership(Long memberId, String role, Long memberOrganizationId,
                                   Long requestOrganizationId, List<String> allowedRoles) {
        if (memberId == null || role == null || role.isBlank() || memberOrganizationId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        }
        if (!memberOrganizationId.equals(requestOrganizationId) || !allowedRoles.contains(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ORG_ACCESS_DENIED");
        }
    }
}
