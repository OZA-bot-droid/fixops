package com.fixops.inventory.request.controller;

import com.fixops.inventory.common.response.ApiResponse;
import com.fixops.inventory.common.response.PageResult;
import com.fixops.inventory.common.security.CurrentMember;
import com.fixops.inventory.common.security.InventoryAuthorization;
import com.fixops.inventory.request.dto.PartRequestCreateRequest;
import com.fixops.inventory.request.dto.PartRequestImageFile;
import com.fixops.inventory.request.dto.PartRequestRejectRequest;
import com.fixops.inventory.request.dto.PartRequestResult;
import com.fixops.inventory.request.service.PartRequestService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 부품 요청 API 입구 (2026-11-27 팀장 승인)
 *   POST /api/inventory/part-requests                    요청 등록          ADMIN, TECHNICIAN
 *   GET  /api/inventory/part-requests?status=            요청 목록          ADMIN: 조직 전체 / TECHNICIAN: 내 요청만
 *   POST /api/inventory/part-requests/{id}/approve       승인 (기록만)      ADMIN
 *   POST /api/inventory/part-requests/{id}/reject        반려 (사유 필수)   ADMIN
 *   POST /api/inventory/part-requests  (multipart)       사진 첨부 등록      ADMIN, TECHNICIAN  (request=JSON, images=사진 1장)
 *   GET  /api/inventory/part-requests/{id}/images/{imageId}  사진 보기    ADMIN: 조직 전체 / TECHNICIAN: 내 요청만
 *
 * 승인·반려를 PATCH 가 아니라 POST /approve, /reject 로 둔 이유
 *   "상태를 아무 값으로 바꾸기"가 아니라 정해진 행동(승인·반려)만 허용하기 위해서다.
 *   REQUESTED 에서만 한 번 바꿀 수 있고, 그 밖은 409.
 *
 * 사용자 확인: Authorization(JWT) + organizationId(+complexId) → InventoryAuthorization 이 Member 에 확인 (D13).
 */
@RestController
@RequestMapping("/inventory/part-requests")
@Validated
public class PartRequestController {

    private final PartRequestService service;
    private final InventoryAuthorization authorization;

    public PartRequestController(PartRequestService service, InventoryAuthorization authorization) {
        this.service = service;
        this.authorization = authorization;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<PartRequestResult>> create(
            @RequestHeader(value = "Authorization", required = false) String bearerToken,   // 로그인 JWT (Gateway 가 확인한 것)
            @RequestParam(required = false) @Positive Long organizationId,                   // 어느 조직에서 일하는지 (Member 가 확인)
            @RequestParam(required = false) @Positive Long complexId,                        // BUILDING 이면 단지 (권한 확인에만 사용)
            @Valid @RequestBody PartRequestCreateRequest request) {

        CurrentMember member = authorization.requireRead(bearerToken, organizationId, complexId);   // ADMIN, TECHNICIAN

        PartRequestResult result = service.create(member.organizationId(), member.memberId(), request);
        return ResponseEntity.created(URI.create("/api/inventory/part-requests/" + result.requestId()))
                .body(ApiResponse.ok(result));   // 201
    }

    /**
     * 사진 첨부 등록 (multipart/form-data).
     *   request : 등록 본문 JSON (위 JSON 등록과 같은 모양, 이 부분의 Content-Type 은 application/json)
     *   images  : 사진 파일 0~1장 (JPEG/PNG/WEBP, 장당 5MB 이하)
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<PartRequestResult>> createWithImages(
            @RequestHeader(value = "Authorization", required = false) String bearerToken,
            @RequestParam(required = false) @Positive Long organizationId,
            @RequestParam(required = false) @Positive Long complexId,
            @Valid @RequestPart("request") PartRequestCreateRequest request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images) {

        CurrentMember member = authorization.requireRead(bearerToken, organizationId, complexId);   // ADMIN, TECHNICIAN

        PartRequestResult result = service.create(member.organizationId(), member.memberId(), request, images);
        return ResponseEntity.created(URI.create("/api/inventory/part-requests/" + result.requestId()))
                .body(ApiResponse.ok(result));   // 201
    }

    /**
     * 첨부 사진 보기. 응답은 JSON 이 아니라 사진 자체(image/jpeg 등).
     * 화면은 로그인 JWT 를 붙여야 해서 img 태그로 바로 부르지 않고, 받아서 보여 준다.
     */
    @GetMapping("/{requestId}/images/{imageId}")
    public ResponseEntity<byte[]> image(
            @RequestHeader(value = "Authorization", required = false) String bearerToken,
            @RequestParam(required = false) @Positive Long organizationId,
            @RequestParam(required = false) @Positive Long complexId,
            @PathVariable @Positive long requestId,
            @PathVariable @Positive long imageId) {

        CurrentMember member = authorization.requireRead(bearerToken, organizationId, complexId);
        Long onlyRequester = member.isAdmin() ? null : member.memberId();   // TECHNICIAN 은 자기 요청 사진만

        PartRequestImageFile file = service.image(member.organizationId(), requestId, imageId, onlyRequester);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .cacheControl(CacheControl.noStore())                // 로그인한 사람만 보는 사진: 캐시에 남기지 않음
                .header("X-Content-Type-Options", "nosniff")        // 브라우저가 다른 형식으로 해석하지 않게
                .body(file.data());
    }

    @GetMapping
    public ApiResponse<PageResult<PartRequestResult>> list(
            @RequestHeader(value = "Authorization", required = false) String bearerToken,   // 로그인 JWT (Gateway 가 확인한 것)
            @RequestParam(required = false) @Positive Long organizationId,                   // 어느 조직에서 일하는지 (Member 가 확인)
            @RequestParam(required = false) @Positive Long complexId,                        // BUILDING 이면 단지 (권한 확인에만 사용)
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        CurrentMember member = authorization.requireRead(bearerToken, organizationId, complexId);

        // TECHNICIAN 은 항상 자기 요청만. 파라미터로 바꿀 수 없게 서버가 정한다
        Long requestedBy = member.isAdmin() ? null : member.memberId();
        return ApiResponse.ok(service.list(member.organizationId(), requestedBy, status, page, size));   // 200
    }

    @PostMapping("/{requestId}/approve")
    public ApiResponse<PartRequestResult> approve(
            @RequestHeader(value = "Authorization", required = false) String bearerToken,   // 로그인 JWT (Gateway 가 확인한 것)
            @RequestParam(required = false) @Positive Long organizationId,                   // 어느 조직에서 일하는지 (Member 가 확인)
            @RequestParam(required = false) @Positive Long complexId,                        // BUILDING 이면 단지 (권한 확인에만 사용)
            @PathVariable @Positive long requestId) {

        CurrentMember member = authorization.requireAdmin(bearerToken, organizationId, complexId);
        return ApiResponse.ok(service.approve(member.organizationId(), member.memberId(), requestId));   // 200
    }

    @PostMapping("/{requestId}/reject")
    public ApiResponse<PartRequestResult> reject(
            @RequestHeader(value = "Authorization", required = false) String bearerToken,   // 로그인 JWT (Gateway 가 확인한 것)
            @RequestParam(required = false) @Positive Long organizationId,                   // 어느 조직에서 일하는지 (Member 가 확인)
            @RequestParam(required = false) @Positive Long complexId,                        // BUILDING 이면 단지 (권한 확인에만 사용)
            @PathVariable @Positive long requestId,
            @Valid @RequestBody PartRequestRejectRequest request) {

        CurrentMember member = authorization.requireAdmin(bearerToken, organizationId, complexId);
        return ApiResponse.ok(service.reject(member.organizationId(), member.memberId(), requestId, request.rejectReason()));   // 200
    }
}
