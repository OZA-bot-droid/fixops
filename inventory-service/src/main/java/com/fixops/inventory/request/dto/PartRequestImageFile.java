package com.fixops.inventory.request.dto;

/** 사진 한 장의 내용 (사진 보기 API 가 돌려주는 값) */
public record PartRequestImageFile(String contentType, byte[] data) {}
