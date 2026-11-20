package com.fixops.inventory.common.exception;

//기존 키에 매핑된 요청 데이터와 새로 들어온 데이터가 일치하지 않을때(IDEMPOTENCY_KEY_REUSE, 409)
public class IdempotencyKeyReuseException extends RuntimeException {
    public IdempotencyKeyReuseException(String message) {
        super(message);
    }
}
