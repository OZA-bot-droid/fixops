package com.fixops.inventory.part.dto;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 부품 정보 수정 요청 본문 (Controller 입구, PATCH).
 * 바꾸고 싶은 항목만 보낸다.
 *   예) {"safetyStock": 30}  → 안전재고만 30 으로, 나머지는 그대로
 * sku, quantity, status 를 보내도 이 클래스에 칸이 없으므로 무시된다.
 */
public record PartUpdateRequest(
        @Size(max = 200) String name,
        @Size(max = 64) String category,
        @Size(max = 16) String unit,
        @PositiveOrZero Integer safetyStock) {}
