package com.fixops.inventory.common.response;

import java.util.List;

/**
 * 목록 응답 공통 모양 (D12). 부품 목록의 PartPageResult 와 같은 필드 이름을 쓴다.
 *   content       : 이번 페이지의 항목들
 *   page, size    : 몇 번째 페이지(0부터), 한 페이지 크기
 *   totalElements : 조건에 맞는 전체 개수
 */
public record PageResult<T>(List<T> content, int page, int size, long totalElements) {}
