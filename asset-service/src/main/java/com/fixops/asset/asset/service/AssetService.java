package com.fixops.asset.asset.service;

import com.fixops.asset.asset.dto.AssetCategoryResponse;
import com.fixops.asset.asset.dto.AssetCategoryCreateRequest;
import com.fixops.asset.asset.dto.AssetCategoryUpdateRequest;
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

    public List<AssetCategoryResponse> categories(Long organizationId, IndustryType industryType) {
        return categories.findAllByOrganizationIdAndIndustryTypeOrderByName(organizationId, industryType).stream()
                .map(AssetCategoryResponse::from).toList();
    }

    /** 중복 이름은 조직·산업 범위의 DB 제약으로 막고, 충돌은 409로 변환한다. */
    @Transactional
    public AssetCategoryResponse createCategory(AssetCategoryCreateRequest request) {
        try {
            AssetCategory category = categories.saveAndFlush(new AssetCategory(
                    request.organizationId(), request.industryType(), request.name().trim()));
            return AssetCategoryResponse.from(category);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ASSET_CATEGORY_ALREADY_EXISTS");
        }
    }

    /** 조직 범위 안에서 이름만 변경해 다른 조직의 카테고리에 영향이 없게 한다. */
    @Transactional
    public AssetCategoryResponse updateCategory(Long id, Long organizationId, AssetCategoryUpdateRequest request) {
        AssetCategory category = findCategory(id, organizationId);
        category.rename(request.name().trim());
        try {
            categories.flush();
            return AssetCategoryResponse.from(category);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ASSET_CATEGORY_ALREADY_EXISTS");
        }
    }

    /** 기존 설비가 참조하는 카테고리는 삭제하지 않아 참조 관계를 보존한다. */
    @Transactional
    public void deleteCategory(Long id, Long organizationId) {
        AssetCategory category = findCategory(id, organizationId);
        if (assets.existsByCategory_IdAndOrganizationId(id, organizationId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ASSET_CATEGORY_IN_USE");
        }
        categories.delete(category);
    }

    @Transactional
    public AssetResponse create(AssetCreateRequest request) {
        Location location = findLocation(request.locationId(), request.organizationId(), request.complexId());
        if (location.getStatus() != LocationStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "INACTIVE_LOCATION");
        }
        // 설비 생성 시 조직 소유와 Location의 산업 유형이 모두 맞는 카테고리만 연결한다.
        AssetCategory category = categories.findByIdAndOrganizationIdAndIndustryType(
                        request.categoryId(), request.organizationId(), location.getLocationType().industryType())
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

    private AssetCategory findCategory(Long id, Long organizationId) {
        // 조직 범위 밖의 카테고리는 존재 여부를 노출하지 않도록 찾을 수 없음으로 처리한다.
        return categories.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND"));
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
