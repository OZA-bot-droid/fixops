package com.fixops.inventory.stock.controller;

import com.fixops.inventory.common.response.ApiResponse;
import com.fixops.inventory.common.response.PageResult;
import com.fixops.inventory.common.security.CurrentMember;
import com.fixops.inventory.common.security.InventoryAuthorization;
import com.fixops.inventory.stock.dto.MovementResult;
import com.fixops.inventory.stock.dto.UsageHistoryResult;
import com.fixops.inventory.stock.service.StockQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 재고 조회 API 입구 (D12, 팀장님 승인 2026-11-27). 읽기 전용.
 *   GET /api/inventory/movements  재고 변동 이력(원장)   ADMIN
 *   GET /api/inventory/usages     작업별 부품 사용 내역   ADMIN, TECHNICIAN
 *
 * 재고를 바꾸는 StockController 와 나눴다 (조회는 잠금·멱등 키가 필요 없다).
 * 사용자 확인: Authorization(JWT) + organizationId(+complexId) → InventoryAuthorization 이 Member 에 확인 (D13).
 */
@RestController
@RequestMapping("/inventory")
@Validated
public class StockQueryController {

    private final StockQueryService queryService;
    private final InventoryAuthorization authorization;

    public StockQueryController(StockQueryService queryService, InventoryAuthorization authorization) {
        this.queryService = queryService;
        this.authorization = authorization;
    }

    /**
     * 재고 변동 이력 (ADMIN 만).
     * 예) GET /api/inventory/movements?partId=92&type=USAGE&page=0&size=20
     */
    @GetMapping("/movements")
    public ApiResponse<PageResult<MovementResult>> movements(
            @RequestHeader(value = "Authorization", required = false) String bearerToken,   // 로그인 JWT (Gateway 가 확인한 것)
            @RequestParam(required = false) @Positive Long organizationId,                   // 어느 조직에서 일하는지 (Member 가 확인)
            @RequestParam(required = false) @Positive Long complexId,                        // BUILDING 이면 단지 (권한 확인에만 사용)
            @RequestParam(required = false) @Positive Long partId,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        CurrentMember member = authorization.requireAdmin(bearerToken, organizationId, complexId);

        return ApiResponse.ok(queryService.movements(member.organizationId(), partId, type, page, size));   // 200
    }

    /**
     * 작업별 부품 사용 내역 (ADMIN, TECHNICIAN).
     * 예) GET /api/inventory/usages?workOrderId=1045
     * 재고는 조직 단위라, 같은 조직의 기술자는 조직의 사용 내역을 볼 수 있다 (2026-11-27 확정).
     */
    @GetMapping("/usages")
    public ApiResponse<PageResult<UsageHistoryResult>> usages(
            @RequestHeader(value = "Authorization", required = false) String bearerToken,   // 로그인 JWT (Gateway 가 확인한 것)
            @RequestParam(required = false) @Positive Long organizationId,                   // 어느 조직에서 일하는지 (Member 가 확인)
            @RequestParam(required = false) @Positive Long complexId,                        // BUILDING 이면 단지 (권한 확인에만 사용)
            @RequestParam(required = false) @Positive Long workOrderId,
            @RequestParam(required = false) @Positive Long partId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        CurrentMember member = authorization.requireRead(bearerToken, organizationId, complexId);

        return ApiResponse.ok(queryService.usages(member.organizationId(), workOrderId, partId, page, size));   // 200
    }
}
