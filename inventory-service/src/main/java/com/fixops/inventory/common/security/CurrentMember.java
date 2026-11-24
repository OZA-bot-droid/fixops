package com.fixops.inventory.common.security;

/**
 * 지금 요청을 보낸 사람 (Gateway가 JWT를 확인한 뒤 헤더로 넘겨준 값).
 * organizationId 는 URL·본문이 아니라 여기서만 꺼낸다. 요청에서 받으면 남의 단지 번호를 넣을 수 있기 때문이다.
 */
public record CurrentMember(long memberId, String role, long organizationId) {

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
