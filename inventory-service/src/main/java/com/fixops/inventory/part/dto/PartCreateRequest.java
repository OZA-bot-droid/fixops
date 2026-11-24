package com.fixops.inventory.part.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 부품 등록 요청 본문 (Controller 입구, OpenAPI PartCreateRequest).
 * 화면이 보내는 JSON 이 이 모양으로 들어온다. organizationId 는 일부러 없다 (헤더에서 꺼냄).
 *
 * 검증 어노테이션은 @Valid 가 붙은 곳에서 동작하고, 틀리면 Service 까지 가지 않고 400 이 된다.
 */
public record PartCreateRequest(
        @NotBlank @Size(max = 64) String sku,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 64) String category,
        @Size(max = 16) String unit,
        @PositiveOrZero Integer safetyStock) {

    /** 요청 + 로그인한 사람의 단지 → Service 가 받는 Command 로 바꾼다. */
    public PartCreateCommand toCommand(long organizationId) {
        return new PartCreateCommand(organizationId, sku, name, category, unit, safetyStock);
    }
}
