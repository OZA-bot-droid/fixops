package com.fixops.inventory.common.exception;

// 로그인은 했지만 이 기능을 쓸 역할이 아닐 때 (ORG_ACCESS_DENIED, 403)
public class OrgAccessDeniedException extends RuntimeException {
    public OrgAccessDeniedException() {
        super("접근할 수 없습니다.");
    }
}
