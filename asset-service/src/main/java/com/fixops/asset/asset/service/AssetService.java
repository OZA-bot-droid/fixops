package com.fixops.asset.asset.service;

import com.fixops.asset.asset.dto.AssetCategoryResponse;
import com.fixops.asset.asset.dto.AssetCreateRequest;
import com.fixops.asset.asset.dto.AssetResponse;
import com.fixops.asset.asset.dto.AssetUpdateRequest;
import com.fixops.asset.asset.entity.Asset;
import com.fixops.asset.asset.entity.AssetCategory;
import com.fixops.asset.asset.repository.AssetCategoryRepository;
import com.fixops.asset.asset.repository.AssetRepository;
import com.fixops.asset.common.enums.IndustryType;
import com.fixops.asset.location.entity.Location;
import com.fixops.asset.location.entity.LocationStatus;
import com.fixops.asset.location.repository.LocationRepository;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 설비와 Location·AssetCategory 간 소유 범위 및 산업 유형을 검증한다. */
@Service
@Transactional(readOnly = true)
public class AssetService {
    private final AssetRepository assets;
    private final AssetCategoryRepository categories;
    private final LocationRepository locations;

    public AssetService(AssetRepository assets, AssetCategoryRepository categories, LocationRepository locations) {
        this.assets = assets;
        this.categories = categories;
        this.locations = locations;
    }

    public List<AssetResponse> list(Long organizationId, Long complexId) {
        List<Asset> result = complexId == null
                ? assets.findAllByOrganizationIdAndComplexIdIsNullOrderByIdDesc(organizationId)
                : assets.findAllByOrganizationIdAndComplexIdOrderByIdDesc(organizationId, complexId);
        return result.stream().map(AssetResponse::from).toList();
    }

    public List<AssetCategoryResponse> categories(IndustryType industryType) {
        return categories.findAllByIndustryTypeOrderByName(industryType).stream()
                .map(AssetCategoryResponse::from).toList();
    }

    @Transactional
    public AssetResponse create(AssetCreateRequest request) {
        Location location = findLocation(request.locationId(), request.organizationId(), request.complexId());
        if (location.getStatus() != LocationStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "INACTIVE_LOCATION");
        }
        AssetCategory category = categories.findByIdAndIndustryType(
                        request.categoryId(), location.getLocationType().industryType())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "ASSET_CATEGORY_INDUSTRY_MISMATCH"));
        try {
            Asset asset = assets.saveAndFlush(new Asset(request.organizationId(), request.complexId(), location,
                    category, request.assetCode().trim(), request.name().trim(), trimToNull(request.description())));
            return AssetResponse.from(asset);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ASSET_CODE_ALREADY_EXISTS");
        }
    }

    @Transactional
    public AssetResponse update(Long id, Long organizationId, Long complexId, AssetUpdateRequest request) {
        Asset asset = findAsset(id, organizationId, complexId);
        asset.update(request.name().trim(), trimToNull(request.description()), request.status());
        assets.flush();
        return AssetResponse.from(asset);
    }

    private Location findLocation(Long id, Long organizationId, Long complexId) {
        return (complexId == null
                ? locations.findByIdAndOrganizationIdAndComplexIdIsNull(id, organizationId)
                : locations.findByIdAndOrganizationIdAndComplexId(id, organizationId, complexId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND"));
    }

    private Asset findAsset(Long id, Long organizationId, Long complexId) {
        return (complexId == null
                ? assets.findByIdAndOrganizationIdAndComplexIdIsNull(id, organizationId)
                : assets.findByIdAndOrganizationIdAndComplexId(id, organizationId, complexId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND"));
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
