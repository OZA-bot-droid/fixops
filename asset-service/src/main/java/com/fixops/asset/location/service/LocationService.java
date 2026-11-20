package com.fixops.asset.location.service;

import com.fixops.asset.location.dto.LocationCreateRequest;
import com.fixops.asset.location.dto.LocationResponse;
import com.fixops.asset.location.dto.LocationUpdateRequest;
import com.fixops.asset.location.entity.Location;
import com.fixops.asset.location.entity.LocationStatus;
import com.fixops.asset.location.entity.LocationType;
import com.fixops.asset.location.repository.LocationRepository;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Location 규칙을 적용하고 저장소 작업을 조정한다. */
@Service
@Transactional(readOnly = true)
public class LocationService {
    private final LocationRepository locations;

    public LocationService(LocationRepository locations) {
        this.locations = locations;
    }

    public List<LocationResponse> list(Long organizationId, Long complexId) {
        // 단지 ID가 생략된 조회는 complex_id IS NULL인 공장 등의 데이터만 반환한다.
        List<Location> result = complexId == null
                ? locations.findAllByOrganizationIdAndComplexIdIsNullOrderById(organizationId)
                : locations.findAllByOrganizationIdAndComplexIdOrderById(organizationId, complexId);
        return result.stream().map(LocationResponse::from).toList();
    }

    @Transactional
    public LocationResponse create(LocationCreateRequest request) {
        // 부모가 있으면 동일 조직·단지 내 활성 노드인지 확인한 뒤 문서상 계층 전이도 검사한다.
        Location parent = null;
        if (request.parentId() != null) {
            parent = findInScope(request.parentId(), request.organizationId(), request.complexId());
            if (parent.getStatus() != LocationStatus.ACTIVE) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "INACTIVE_PARENT_LOCATION");
            }
            if (!parent.getLocationType().canContain(request.locationType())) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_LOCATION_HIERARCHY");
            }
        } else if (!request.locationType().isRoot()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "ROOT_LOCATION_TYPE_REQUIRED");
        }
        Location saved = locations.saveAndFlush(new Location(request.organizationId(), request.complexId(), parent,
                request.locationType(), request.code().trim(), request.name().trim()));
        return LocationResponse.from(saved);
    }

    @Transactional
    public LocationResponse update(Long id, Long organizationId, Long complexId,
                                   LocationUpdateRequest request) {
        Location location = findInScope(id, organizationId, complexId);
        location.update(request.code().trim(), request.name().trim(), request.status());
        locations.flush();
        return LocationResponse.from(location);
    }

    private Location findInScope(Long id, Long organizationId, Long complexId) {
        // 범위 밖 ID는 존재 여부를 노출하지 않도록 동일하게 404 처리한다.
        return (complexId == null
                ? locations.findByIdAndOrganizationIdAndComplexIdIsNull(id, organizationId)
                : locations.findByIdAndOrganizationIdAndComplexId(id, organizationId, complexId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND"));
    }
}
