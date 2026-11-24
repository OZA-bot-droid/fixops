package com.fixops.inventory.dto;

/**
 * 부품 목록 조회 조건 (Service 입구). 페이지 정보(Pageable)는 따로 받는다.
 * keyword 가 null 이거나 비어 있으면 검색어 조건 없이 전체를 본다.
 */
public record PartSearchCommand(
        long organizationId,
        String keyword,
        PartStatusFilter status,
        boolean lowStockOnly) {

    public PartSearchCommand {
        if (status == null) {
            status = PartStatusFilter.ACTIVE;   // OpenAPI 기본값
        }
        if (keyword != null && keyword.length() > 100) {
            throw new IllegalArgumentException("검색어는 100자 이하입니다.");
        }
    }
}
