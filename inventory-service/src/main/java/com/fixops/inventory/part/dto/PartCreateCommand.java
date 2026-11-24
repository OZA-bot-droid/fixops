package com.fixops.inventory.part.dto;

/**
 * 부품 등록에 필요한 값 (Service 입구).
 * Controller가 요청(PartCreateRequest)과 로그인 정보(organizationId)를 합쳐서 만든다.
 * category, unit, safetyStock 은 비어 있어도 된다 (기본값은 Part.create 가 채운다).
 */
public record PartCreateCommand(
        long organizationId,
        String sku,
        String name,
        String category,
        String unit,
        Integer safetyStock) {

    public PartCreateCommand {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku는 필수입니다.");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name은 필수입니다.");
        }
        if (safetyStock != null && safetyStock < 0) {
            throw new IllegalArgumentException("safetyStock은 0 이상이어야 합니다.");
        }
        // 앞뒤 공백 제거: " BOLT-M8" 과 "BOLT-M8" 이 서로 다른 SKU로 등록되는 것을 막는다.
        sku = sku.trim();
        name = name.trim();
    }
}
