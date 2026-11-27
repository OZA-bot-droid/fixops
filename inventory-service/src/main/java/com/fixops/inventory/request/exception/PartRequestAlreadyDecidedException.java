package com.fixops.inventory.request.exception;

// 이미 승인·반려된 요청을 다시 처리하려 할 때 (INVALID_STATE_TRANSITION, 409)
// 관리자 두 명이 동시에 누른 경우에도 한 명만 성공하고 나머지는 이 예외를 받는다.
public class PartRequestAlreadyDecidedException extends RuntimeException {
    public PartRequestAlreadyDecidedException(long requestId, String status) {
        super("이미 처리된 부품 요청입니다: requestId=" + requestId + ", status=" + status);
    }
}
