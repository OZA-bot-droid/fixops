package com.fixops.inventory.request.dto;

/**
 * 부품 요청에 첨부한 사진 정보 (사진 내용은 빼고 정보만).
 * 실제 사진은 GET /inventory/part-requests/{requestId}/images/{imageId} 로 따로 받는다
 * (목록 응답에 사진을 넣으면 응답이 너무 커지기 때문).
 */
public record PartRequestImageInfo(long imageId, String fileName, String contentType, int sizeBytes) {}
