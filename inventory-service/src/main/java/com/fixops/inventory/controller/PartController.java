package com.fixops.inventory.controller;

import com.fixops.inventory.common.response.ApiResponse;
import com.fixops.inventory.common.security.CurrentMember;
import com.fixops.inventory.common.security.InventoryAuthorization;
import com.fixops.inventory.dto.PartCreateRequest;
import com.fixops.inventory.dto.PartPageResult;
import com.fixops.inventory.dto.PartResult;
import com.fixops.inventory.dto.PartSearchCommand;
import com.fixops.inventory.dto.PartStatusFilter;
import com.fixops.inventory.service.PartService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 부품 API 입구 (OpenAPI: GET/POST /api/inventory/parts).
 *
 * [주소가 /inventory/parts 인 이유]
 *   Gateway 가 /api/inventory/** 를 받아 앞의 /api 를 떼고(stripPrefix(1)) 여기로 넘긴다.
 *   그래서 화면은 /api/inventory/parts 로 부르고, 이 서비스에는 /inventory/parts 로 도착한다.
 *
 * [Controller 가 하는 일]
 *   1) 요청에서 값을 꺼낸다 (헤더, URL 파라미터, 본문)
 *   2) 권한을 확인한다
 *   3) Command 로 바꿔 Service 를 부른다
 *   4) 결과를 공통 응답 모양(ApiResponse)에 담아 돌려준다
 *   업무 규칙(SKU 중복 등)은 여기서 판단하지 않는다. 그건 Service 의 일이다.
 */
@RestController
@RequestMapping("/inventory/parts")
@Validated   // @RequestParam 에 붙인 @Min, @Max 같은 검증을 동작시킨다
public class PartController {

    // 정렬 허용 목록. SQL 별칭과 같은 이름만 허용한다 (StockBalanceRepository 주석 참고).
    private static final Set<String> SORTABLE = Set.of("sku", "name", "quantity");

    private final PartService partService;
    private final InventoryAuthorization authorization;

    public PartController(PartService partService, InventoryAuthorization authorization) {
        this.partService = partService;
        this.authorization = authorization;
    }

    /**
     * 부품 목록 조회.
     * 예) GET /api/inventory/parts?q=볼트&lowStockOnly=true&page=0&size=20&sort=name,asc
     */
    @GetMapping
    public ApiResponse<PartPageResult> list(
            @RequestHeader(value = "X-Member-Id", required = false) Long memberId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Org-Id", required = false) Long organizationId,
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(defaultValue = "ACTIVE") PartStatusFilter status,
            @RequestParam(defaultValue = "false") boolean lowStockOnly,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "sku,asc") String sort) {

        CurrentMember member = authorization.requireRead(memberId, role, organizationId);

        PartSearchCommand command = new PartSearchCommand(member.organizationId(), q, status, lowStockOnly);
        Pageable pageable = PageRequest.of(page, size, toSort(sort));

        return ApiResponse.ok(partService.search(command, pageable));   // 200
    }

    /**
     * 부품 등록 (ADMIN 만). 성공하면 201 Created 와 새 부품 주소(Location 헤더)를 돌려준다.
     *
     * Idempotency-Key 는 계약상 필수라 받기는 하지만, 부품은 "단지 + SKU" 가 이미 유일해서
     * 같은 요청을 두 번 보내도 두 번째는 409 가 된다. 키 저장은 입고 API 를 만들 때 함께 다룬다.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<PartResult>> create(
            @RequestHeader(value = "X-Member-Id", required = false) Long memberId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Org-Id", required = false) Long organizationId,
            @RequestHeader("Idempotency-Key") @Size(min = 8, max = 100) String idempotencyKey,
            @Valid @RequestBody PartCreateRequest request) {

        CurrentMember member = authorization.requireAdmin(memberId, role, organizationId);

        PartResult created = partService.create(request.toCommand(member.organizationId()));

        return ResponseEntity
                .created(URI.create("/api/inventory/parts/" + created.id()))   // 201 + Location 헤더
                .body(ApiResponse.ok(created));
    }

    /** "name,asc" → Sort. 허용하지 않은 이름이나 방향이면 400 이 되도록 IllegalArgumentException. */
    private static Sort toSort(String sort) {
        String[] parts = sort.split(",");
        String property = parts[0].trim();
        if (!SORTABLE.contains(property)) {
            throw new IllegalArgumentException("정렬할 수 없는 항목입니다: " + property);
        }
        Sort.Direction direction = parts.length > 1
                ? Sort.Direction.fromString(parts[1].trim())   // asc, desc 외에는 예외
                : Sort.Direction.ASC;
        return Sort.by(direction, property);
    }
}
