package com.fixops.asset.location.controller;

import com.fixops.asset.common.security.AssetAuthorization;
import com.fixops.asset.location.dto.LocationCreateRequest;
import com.fixops.asset.location.dto.LocationResponse;
import com.fixops.asset.location.dto.LocationUpdateRequest;
import com.fixops.asset.location.service.LocationService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Location HTTP 경로. 입력 검증과 역할 확인 뒤 서비스 계층으로 위임한다. */
@RestController
@RequestMapping("/api/locations")
@Validated
public class LocationController {
    private final LocationService locations;
    private final AssetAuthorization authorization;

    public LocationController(LocationService locations, AssetAuthorization authorization) {
        this.locations = locations;
        this.authorization = authorization;
    }

    @GetMapping
    public List<LocationResponse> list(
            @RequestHeader(value = "Authorization", required = false) String bearer,
            @RequestParam @Positive Long organizationId,
            @RequestParam(required = false) @Positive Long complexId) {
        authorization.requireRead(bearer, organizationId, complexId);
        return locations.list(organizationId, complexId);
    }

    @PostMapping
    public ResponseEntity<LocationResponse> create(
            @RequestHeader(value = "Authorization", required = false) String bearer,
            @Valid @RequestBody LocationCreateRequest request) {
        authorization.requireWrite(bearer, request.organizationId(), request.complexId());
        LocationResponse created = locations.create(request);
        return ResponseEntity.created(URI.create("/api/locations/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public LocationResponse update(
            @RequestHeader(value = "Authorization", required = false) String bearer,
            @PathVariable @Positive Long id,
            @RequestParam @Positive Long organizationId,
            @RequestParam(required = false) @Positive Long complexId,
            @Valid @RequestBody LocationUpdateRequest request) {
        authorization.requireWrite(bearer, organizationId, complexId);
        return locations.update(id, organizationId, complexId, request);
    }
}
