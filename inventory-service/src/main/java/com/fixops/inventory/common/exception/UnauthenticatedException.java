package com.fixops.inventory.common.exception;

// 로그인 정보(헤더)가 없을 때 (UNAUTHENTICATED, 401)
public class UnauthenticatedException extends RuntimeException {
    public UnauthenticatedException() {
        super("로그인이 필요합니다.");
    }
}
