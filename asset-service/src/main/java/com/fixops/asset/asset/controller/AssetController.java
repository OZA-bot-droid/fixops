package com.fixops.asset.asset.controller;

import com.fixops.asset.asset.dto.AssetCategoryResponse;
import com.fixops.asset.asset.dto.AssetCategoryCreateRequest;
import com.fixops.asset.asset.dto.AssetCategoryUpdateRequest;
import com.fixops.asset.asset.dto.AssetCreateRequest;
import com.fixops.asset.asset.dto.AssetResponse;
import com.fixops.asset.asset.dto.AssetUpdateRequest;
import com.fixops.asset.asset.service.AssetService;
import com.fixops.asset.common.security.AssetAuthorization;
import com.fixops.asset.common.enums.IndustryType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 설비 CRUD 및 조직별 카테고리 관리 HTTP API. */
@RestController
@RequestMapping("/api/assets")
@Validated
public class AssetController {
    private final AssetService assets;
    private final AssetAuthorization authorization;

    public AssetController(AssetService assets, AssetAuthorization authorization) {
        this.assets = assets;
        this.authorization = authorization;
    }

    @GetMapping
    public List<AssetResponse> list(
            @RequestHeader(value = "X-Member-Id", required = false) Long memberId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Org-Id", required = false) Long memberOrganizationId,
            @RequestParam @Positive Long organizationId,
            @RequestParam(required = false) @Positive Long complexId) {
        authorization.requireRead(memberId, role, memberOrganizationId, organizationId);
        return assets.list(organizationId, complexId);
    }

    /** 로그인한 조직의 산업 유형별 설비 카테고리만 조회한다. */
    @GetMapping("/categories")
    public List<AssetCategoryResponse> categories(
            @RequestHeader(value = "X-Member-Id", required = false) Long memberId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Org-Id", required = false) Long memberOrganizationId,
            @RequestParam @Positive Long organizationId,
            @RequestParam IndustryType industryType) {
        authorization.requireRead(memberId, role, memberOrganizationId, organizationId);
        return assets.categories(organizationId, industryType);
    }

    /** 조직 관리자가 해당 조직에 새 설비 카테고리를 등록한다. */
    @PostMapping("/categories")
    public ResponseEntity<AssetCategoryResponse> createCategory(
            @RequestHeader(value = "X-Member-Id", required = false) Long memberId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Org-Id", required = false) Long memberOrganizationId,
            @Valid @RequestBody AssetCategoryCreateRequest request) {
        authorization.requireWrite(memberId, role, memberOrganizationId, request.organizationId());
        AssetCategoryResponse created = assets.createCategory(request);
        return ResponseEntity.created(URI.create("/api/assets/categories/" + created.id())).body(created);
    }

    /** 카테고리의 산업 유형은 유지하고 이름만 수정한다. */
    @PatchMapping("/categories/{id}")
    public AssetCategoryResponse updateCategory(
            @RequestHeader(value = "X-Member-Id", required = false) Long memberId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Org-Id", required = false) Long memberOrganizationId,
            @PathVariable @Positive Long id,
            @RequestParam @Positive Long organizationId,
            @Valid @RequestBody AssetCategoryUpdateRequest request) {
        authorization.requireWrite(memberId, role, memberOrganizationId, organizationId);
        return assets.updateCategory(id, organizationId, request);
    }

    /** 설비가 참조 중이지 않은 조직 카테고리만 삭제한다. */
    @DeleteMapping("/categories/{id}")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(
            @RequestHeader(value = "X-Member-Id", required = false) Long memberId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Org-Id", required = false) Long memberOrganizationId,
            @PathVariable @Positive Long id,
            @RequestParam @Positive Long organizationId) {
        authorization.requireWrite(memberId, role, memberOrganizationId, organizationId);
        assets.deleteCategory(id, organizationId);
    }

    @PostMapping
    public ResponseEntity<AssetResponse> create(
            @RequestHeader(value = "X-Member-Id", required = false) Long memberId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Org-Id", required = false) Long memberOrganizationId,
            @Valid @RequestBody AssetCreateRequest request) {
        authorization.requireWrite(memberId, role, memberOrganizationId, request.organizationId());
        AssetResponse created = assets.create(request);
        return ResponseEntity.created(URI.create("/api/assets/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    public AssetResponse update(
            @RequestHeader(value = "X-Member-Id", required = false) Long memberId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Org-Id", required = false) Long memberOrganizationId,
            @PathVariable @Positive Long id,
            @RequestParam @Positive Long organizationId,
            @RequestParam(required = false) @Positive Long complexId,
            @Valid @RequestBody AssetUpdateRequest request) {
        authorization.requireWrite(memberId, role, memberOrganizationId, organizationId);
        return assets.update(id, organizationId, complexId, request);
    }
}
