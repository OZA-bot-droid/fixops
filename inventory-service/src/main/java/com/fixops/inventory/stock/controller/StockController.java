package com.fixops.inventory.stock.controller;

import com.fixops.inventory.common.response.ApiResponse;
import com.fixops.inventory.common.security.CurrentMember;
import com.fixops.inventory.common.security.InventoryAuthorization;
import com.fixops.inventory.stock.dto.ReceiptRequest;
import com.fixops.inventory.stock.dto.StockChangeResult;
import com.fixops.inventory.stock.dto.UsageRequest;
import com.fixops.inventory.stock.dto.UsageResult;
import com.fixops.inventory.stock.service.StockService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 재고 변동 API 입구 (OpenAPI: POST /api/inventory/receipts, POST /api/inventory/usages).
 * Gateway 가 /api 를 떼고 넘기므로 이 서비스에는 /inventory/... 로 도착한다.
 */
@RestController
@RequestMapping("/inventory")
public class StockController {

    private final StockService stockService;
    private final InventoryAuthorization authorization;

    public StockController(StockService stockService, InventoryAuthorization authorization) {
        this.stockService = stockService;
        this.authorization = authorization;
    }

    /**
     * 입고 (ADMIN 만).
     *   새로 처리하면 201, 같은 키로 다시 오면(재시도) 처음 결과를 200 으로 돌려준다.
     *
     * Idempotency-Key 헤더와 본문 operationKey 는 같은 값이어야 한다 (D7 결정).
     * 두 값이 다르면 어느 것이 진짜인지 알 수 없으므로 400 으로 거부한다.
     */
    @PostMapping("/receipts")
    public ResponseEntity<ApiResponse<StockChangeResult>> receive(
            @RequestHeader(value = "X-Member-Id", required = false) Long memberId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Org-Id", required = false) Long organizationId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ReceiptRequest request) {

        CurrentMember member = authorization.requireAdmin(memberId, role, organizationId);

        if (!idempotencyKey.equals(request.operationKey())) {
            throw new IllegalArgumentException("Idempotency-Key 와 operationKey 가 다릅니다.");
        }

        StockChangeResult result = stockService.receive(member.organizationId(), member.memberId(), request);

        HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(ApiResponse.ok(result));
    }

    /**
     * 부품 사용 = 재고 차감 (ADMIN 또는 배정된 TECHNICIAN, D8).
     *   새로 처리하면 201, 같은 키로 다시 오면(재시도) 처음 결과를 200 으로 돌려준다 (입고와 같음).
     *
     * 여기서는 역할(ADMIN/TECHNICIAN)과 헤더 키만 확인한다.
     * "배정된 기사인가, 작업이 진행 중인가"는 WorkOrder 정보가 있어야 알 수 있어서 StockService.use() 가 확인한다.
     */
    @PostMapping("/usages")
    public ResponseEntity<ApiResponse<UsageResult>> use(
            @RequestHeader(value = "X-Member-Id", required = false) Long memberId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Org-Id", required = false) Long organizationId,
            @RequestHeader(value = "Authorization", required = false) String bearerToken, // D11: WorkOrder 로 전달할 사용자 JWT
            // (이름을 authorization 으로 하면 위의 권한 확인 필드 authorization 을 가려서 컴파일 오류가 난다)
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody UsageRequest request) {

        CurrentMember member = authorization.requireUsage(memberId, role, organizationId);

        if (!idempotencyKey.equals(request.operationKey())) {
            throw new IllegalArgumentException("Idempotency-Key 와 operationKey 가 다릅니다.");
        }

        UsageResult result = stockService.use(member, request, bearerToken);

        HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(ApiResponse.ok(result));
    }
}
