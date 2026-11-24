package com.fixops.inventory.part.exception;

/**
 * 비활성(INACTIVE) 부품에 입고하려 할 때 (PART_INACTIVE, 409).
 * 비활성은 "안 쓰는 부품"이라 재고를 늘리지 않는다. 다시 쓰려면 먼저 활성화해야 한다 (방법은 팀장 결정 대기).
 */
public class PartInactiveException extends RuntimeException {
    public PartInactiveException(long partId) {
        super("비활성 부품입니다: partId=" + partId);
    }
}
