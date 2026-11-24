package com.fixops.workorder.workorder.controller;

import com.fixops.workorder.common.response.ApiResponse;
import com.fixops.workorder.workorder.domain.WorkOrderPriority;
import com.fixops.workorder.workorder.domain.WorkOrderStatus;
import com.fixops.workorder.workorder.dto.WorkOrderAssignDTO;
import com.fixops.workorder.workorder.dto.WorkOrderSaveDTO;
import com.fixops.workorder.workorder.dto.WorkOrderStatusDTO;
import com.fixops.workorder.workorder.service.WorkOrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/work-orders")
@RequiredArgsConstructor
public class WorkOrderController {

    private final WorkOrderService workOrderService;


    // 신고를 토대로 혹은 직접 작업지시 등록
    @PostMapping
    public ResponseEntity<?> createWorkOrder(@Valid @RequestBody WorkOrderSaveDTO dto, @RequestHeader("X-Member-Id") Long userId) {
        return new ResponseEntity<>(ApiResponse.success(workOrderService.createWorkOrder(dto, userId)), HttpStatus.CREATED);
    }

    // 관리자 - 조직 전체 및 필터별 작업지시 목록 조회
    @GetMapping
    public ResponseEntity<?> getWorkOrders(@RequestHeader("X-Member-Id") Long userId, @RequestParam Long organizationId,
                                           @RequestParam(required = false) Long complexId, @RequestParam(required = false) Long locationId,
                                           @RequestParam(required = false) Long assetId, @RequestParam(required = false) String title,
                                           @RequestParam(required = false) WorkOrderStatus status, @RequestParam(required = false) WorkOrderPriority priority,
                                           @RequestParam(required = false) Long assignedTo, @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "10") int size) {

        return ResponseEntity.ok(ApiResponse.success(workOrderService.getWorkOrders(userId, organizationId, complexId, locationId, assetId,
                                                    title, status, priority, assignedTo, page, size)));
    }

    // 작업지시 상세 조회
    @GetMapping("/{id}")
    public ResponseEntity<?> getWorkOrder(@PathVariable Long id, @RequestHeader("X-Member-Id") Long userId, @RequestParam Long organizationId) {
        return ResponseEntity.ok(ApiResponse.success(workOrderService.getWorkOrder(id, userId, organizationId)));
    }

    // 작업자 배정/재배정
    @PatchMapping("/{id}/assignment")
    public ResponseEntity<?> assignWorkOrder(@PathVariable Long id, @Valid @RequestBody WorkOrderAssignDTO dto, @RequestHeader("X-Member-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.success(workOrderService.assignWorkOrder(id, dto, userId)));
    }

    // 작업 상태 변경
    @PatchMapping("/{id}/status")
    public ResponseEntity<?> changeWorkOrderStatus(@PathVariable Long id, @Valid @RequestBody WorkOrderStatusDTO dto, @RequestHeader("X-Member-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.success(workOrderService.changeWorkOrderStatus(id, dto, userId)));
    }

    // 작업 상태 변경 이력 조회
    @GetMapping("/{id}/history")
    public ResponseEntity<?> getWorkOrderHistory(@PathVariable Long id, @RequestHeader("X-Member-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.success(workOrderService.getWorkOrderHistory(id, userId)));
    }

    // 작업자 배정·재배정 이력 조회
    @GetMapping("/{id}/assignment-history")
    public ResponseEntity<?> getAssignmentHistory(@PathVariable Long id, @RequestHeader("X-Member-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.success(workOrderService.getAssignmentHistory(id, userId)));
    }

    // 작업자 본인에게 배정된 작업 조회
    @GetMapping("/my")
    public ResponseEntity<?> getMyWorkOrders(@RequestHeader("X-Member-Id") Long userId, @RequestParam Long organizationId,
                                             @RequestParam(required = false) Long complexId, @RequestParam(required = false) Long locationId,
                                             @RequestParam(required = false) Long assetId, @RequestParam(required = false) String title,
                                             @RequestParam(required = false) WorkOrderStatus status, @RequestParam(required = false) WorkOrderPriority priority,
                                             @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success(workOrderService.getMyWorkOrders(userId, organizationId, complexId,
                                                                                locationId, assetId, title, status, priority, page, size)));
    }
}
