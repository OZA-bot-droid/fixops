package com.fixops.inventory.dto.part;

/**
 * 부품 정보 수정에 필요한 값 (Service 입구).
 * null 인 항목은 "바꾸지 않음"이다. 최소 한 항목은 있어야 한다.
 * sku 와 재고 수량은 일부러 없다 (SKU 는 식별자, 수량은 입고·사용으로만 변경).
 */
public record PartUpdateCommand(
        long organizationId,
        long partId,
        String name,
        String category,
        String unit,
        Integer safetyStock) {

    public PartUpdateCommand {
        if (name == null && category == null && unit == null && safetyStock == null) {
            throw new IllegalArgumentException("수정할 항목이 없습니다.");
        }
        // 보냈다면 비어 있으면 안 되는 항목 (null 은 "안 바꿈"이라 통과)
        if (name != null && name.isBlank()) {
            throw new IllegalArgumentException("name은 비워 둘 수 없습니다.");
        }
        if (unit != null && unit.isBlank()) {
            throw new IllegalArgumentException("unit은 비워 둘 수 없습니다.");
        }
        if (safetyStock != null && safetyStock < 0) {
            throw new IllegalArgumentException("safetyStock은 0 이상이어야 합니다.");
        }
        name = (name == null) ? null : name.trim();
        unit = (unit == null) ? null : unit.trim();
    }
}
