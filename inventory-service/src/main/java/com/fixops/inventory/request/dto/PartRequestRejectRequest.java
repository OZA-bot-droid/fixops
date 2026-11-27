package com.fixops.inventory.request.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 반려 본문. 사유는 필수 (요청한 기술자에게 그대로 보인다) */
public record PartRequestRejectRequest(@NotBlank @Size(max = 500) String rejectReason) {}
