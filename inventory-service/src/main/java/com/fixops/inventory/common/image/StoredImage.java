package com.fixops.inventory.common.image;

/** DB 에서 꺼낸 사진 한 장 (사진 보기 API 가 돌려주는 값) */
public record StoredImage(String contentType, byte[] data) {}
