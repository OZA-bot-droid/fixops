package com.fixops.asset.asset.dto;

import com.fixops.asset.asset.entity.Asset;
import com.fixops.asset.asset.entity.AssetStatus;
import java.time.Instant;

/** API 응답 전용 설비 표현. */
public record AssetResponse(
        Long id,
        Long organizationId,
        Long complexId,
        Long locationId,
        Long categoryId,
        String categoryName,
        String assetCode,
        String name,
        String description,
        AssetStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public static AssetResponse from(Asset asset) {
        return new AssetResponse(asset.getId(), asset.getOrganizationId(), asset.getComplexId(),
                asset.getLocationId(), asset.getCategoryId(), asset.getCategoryName(), asset.getAssetCode(),
                asset.getName(), asset.getDescription(), asset.getStatus(), asset.getCreatedAt(), asset.getUpdatedAt());
    }
}
