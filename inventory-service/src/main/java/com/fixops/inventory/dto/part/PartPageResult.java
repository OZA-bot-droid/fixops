package com.fixops.inventory.dto.part;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * 부품 목록 결과. OpenAPI 의 PartPage 스키마(content, page, size, totalElements)와 같은 모양.
 * Spring 의 Page 를 그대로 내보내면 필드가 많고 형식이 계약과 달라서 필요한 것만 옮긴다.
 */
public record PartPageResult(
        List<PartResult> content,
        int page,
        int size,
        long totalElements) {

    public static PartPageResult from(Page<PartResult> page) {
        return new PartPageResult(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
