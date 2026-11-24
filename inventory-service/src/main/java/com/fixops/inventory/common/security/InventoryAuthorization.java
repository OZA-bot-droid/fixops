package com.fixops.inventory.common.security;

import com.fixops.inventory.common.exception.auth.OrgAccessDeniedException;
import com.fixops.inventory.common.exception.auth.UnauthenticatedException;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 헤더 값으로 "누가 무엇을 할 수 있는지" 확인한다 (Asset 의 LocationAuthorization 과 같은 방식).
 *
 * 주의: 외부에서 직접 보낸 X-* 헤더는 Gateway 가 지운다 (StripIdentityHeaders).
 *       Gateway 가 JWT 에서 값을 꺼내 헤더를 다시 넣어 주는 부분은 Gateway 담당(오좌언)이 연결해야 한다.
 */
@Component
public class InventoryAuthorization {

    private static final Set<String> READ_ROLES = Set.of("ADMIN", "TECHNICIAN");

    /** 조회: ADMIN, TECHNICIAN */
    public CurrentMember requireRead(Long memberId, String role, Long organizationId) {
        CurrentMember member = requireLogin(memberId, role, organizationId);
        if (!READ_ROLES.contains(member.role())) {
            throw new OrgAccessDeniedException();
        }
        return member;
    }

    /** 부품 등록·입고: ADMIN 만 (OpenAPI 계약) */
    public CurrentMember requireAdmin(Long memberId, String role, Long organizationId) {
        CurrentMember member = requireLogin(memberId, role, organizationId);
        if (!member.isAdmin()) {
            throw new OrgAccessDeniedException();
        }
        return member;
    }

    // 셋 중 하나라도 없으면 로그인 안 한 것으로 본다 (401)
    private CurrentMember requireLogin(Long memberId, String role, Long organizationId) {
        if (memberId == null || role == null || role.isBlank() || organizationId == null) {
            throw new UnauthenticatedException();
        }
        return new CurrentMember(memberId, role, organizationId);
    }
}
