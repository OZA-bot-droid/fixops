package com.fixops.inventory.part.exception;

/**
 * 부품이 없거나 다른 조직의 부품일 때 (RESOURCE_NOT_FOUND, 404).
 * 다른 조직 부품도 "없다"로 답한다. "있지만 권한 없음"이라고 하면 남의 부품 번호가 존재한다는 사실이 드러난다.
 */
public class PartNotFoundException extends RuntimeException {
    public PartNotFoundException(long partId) {
        super("부품을 찾을 수 없습니다: partId=" + partId);
    }
}
