package com.fixops.asset.location.dto;

import com.fixops.asset.location.entity.Location;
import com.fixops.asset.location.entity.LocationStatus;
import com.fixops.asset.location.entity.LocationType;
import java.time.Instant;

/** 클라이언트에 반환하는 Location 값. JPA Entity를 직접 반환하지 않는다. */
public record LocationResponse(
        Long id,
        Long organizationId,
        Long complexId,
        Long parentId,
        LocationType locationType,
        String code,
        String name,
        LocationStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public static LocationResponse from(Location location) {
        return new LocationResponse(location.getId(), location.getOrganizationId(), location.getComplexId(),
                location.getParentId(), location.getLocationType(), location.getCode(), location.getName(),
                location.getStatus(), location.getCreatedAt(), location.getUpdatedAt());
    }
}
