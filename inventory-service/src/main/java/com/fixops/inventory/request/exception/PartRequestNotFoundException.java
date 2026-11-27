package com.fixops.inventory.request.exception;

// 요청이 없거나 다른 조직의 요청일 때 (RESOURCE_NOT_FOUND, 404)
public class PartRequestNotFoundException extends RuntimeException {
    public PartRequestNotFoundException(long requestId) {
        super("부품 요청을 찾을 수 없습니다: requestId=" + requestId);
    }
}
