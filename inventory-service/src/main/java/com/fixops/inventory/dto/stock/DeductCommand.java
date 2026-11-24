package com.fixops.inventory.dto.stock;

/**
 * 재고 차감(부품 사용)에 필요한 값 (Service 입구).
 *
 * organizationId : 어느 단지의 재고인지 (로그인 정보에서)
 * workOrderId    : 어느 작업에서 쓴 부품인지 (WorkOrder 서비스의 작업 번호)
 * partId, quantity : 무엇을 몇 개 썼는지
 * operationKey   : 멱등 키. 같은 키로 다시 오면 차감하지 않고 처음 결과를 돌려준다
 * actorId        : 누가 차감했는지 (원장 created_by 에 기록)
 *
 * 아래 compact constructor 가 만들어지는 순간 검사한다. 잘못된 값으로는 객체 자체가 만들어지지 않는다.
 */
public record DeductCommand(
        long organizationId,
        long workOrderId,
        long partId,
        int quantity,
        String operationKey,
        long actorId) {

    public DeductCommand {
        if (quantity <= 0) {
            throw new IllegalArgumentException("수량이 0보다 커야합니다.");
        }
        if (operationKey == null || operationKey.isBlank()) {
            throw new IllegalArgumentException("operationKey값이 필수적으로 필요하다");
        }
    }
}
