package com.fixops.inventory.common.security;

/**
 * 지금 요청을 보낸 사람. InventoryAuthorization 이 Member 에 확인한 결과다 (D13).
 * organizationId 는 요청 파라미터를 그대로 쓰지 않고, Member 가 "이 사람은 이 조직 구성원이 맞다"고 확인한 값만 쓴다.
 * 그래서 Service 는 이 값으로만 데이터를 거른다 (남의 조직 데이터 접근 방지).
 */
public record CurrentMember(long memberId, String role, long organizationId) {

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
