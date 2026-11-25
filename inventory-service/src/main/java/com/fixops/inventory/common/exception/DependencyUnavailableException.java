package com.fixops.inventory.common.exception;

/**
 * 다른 서비스(WorkOrder 등)에 연결할 수 없을 때 (DEPENDENCY_UNAVAILABLE, 503).
 * 이 예외가 나면 재고를 바꾸지 않는다. 화면은 잠시 후 같은 operationKey 로 다시 시도하면 된다.
 * 어느 기능에서나 날 수 있어서 common 에 둔다.
 */
public class DependencyUnavailableException extends RuntimeException {
    public DependencyUnavailableException(String message) {
        super(message);
    }
}
