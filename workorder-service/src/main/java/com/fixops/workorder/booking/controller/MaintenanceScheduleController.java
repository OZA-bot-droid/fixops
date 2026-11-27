package com.fixops.workorder.booking.controller;

import com.fixops.workorder.booking.dto.ScheduleReadDTO;
import com.fixops.workorder.booking.dto.ScheduleSaveDTO;
import com.fixops.workorder.booking.dto.ScheduleSearchDTO;
import com.fixops.workorder.booking.service.BookingIdempotencyService;
import com.fixops.workorder.booking.service.MaintenanceScheduleService;
import com.fixops.workorder.booking.dto.ScheduleCancelDTO;
import com.fixops.workorder.booking.service.ScheduleQueryService;
import com.fixops.workorder.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;

@RestController
@RequestMapping("/schedules")
@RequiredArgsConstructor
public class MaintenanceScheduleController {

    private final MaintenanceScheduleService scheduleService;
    private final ScheduleQueryService scheduleQueryService;

    // 최초 방문 예약 요청
    @PostMapping
    public ResponseEntity<String> createSchedule(@RequestHeader("X-Member-Id") Long userId, @RequestHeader("Idempotency-Key") String requestKey,
                                                 @Valid @RequestBody ScheduleSaveDTO dto) {

        BookingIdempotencyService.StoredResponse response = scheduleService.createSchedule(dto, userId, requestKey);

        return ResponseEntity.status(response.status()).contentType(MediaType.APPLICATION_JSON).body(response.body());
    }


    // 예약 취소
    @PostMapping("/{scheduleId}/cancel")
    public ResponseEntity<String> cancelSchedule(@RequestHeader("X-Member-Id") Long userId, @RequestHeader("Idempotency-Key") String requestKey,
                                                 @PathVariable Long scheduleId, @Valid @RequestBody ScheduleCancelDTO dto) {

        BookingIdempotencyService.StoredResponse response = scheduleService.cancelSchedule(scheduleId, dto, userId, requestKey);


        return ResponseEntity.status(response.status()).contentType(MediaType.APPLICATION_JSON).body(response.body());
    }

    // 방문 완료
    @PostMapping("/{scheduleId}/complete")
    public ResponseEntity<String> completeSchedule(@RequestHeader("X-Member-Id") Long userId, @RequestHeader("Idempotency-Key") String requestKey,
                                                   @PathVariable Long scheduleId) {

        BookingIdempotencyService.StoredResponse response = scheduleService.completeSchedule(scheduleId, userId, requestKey);


        return ResponseEntity.status(response.status()).contentType(MediaType.APPLICATION_JSON).body(response.body());
    }


    // 관리자 예약 목록 조회
    @GetMapping
    public ResponseEntity<ApiResponse<Page<ScheduleReadDTO>>> getSchedules(@RequestHeader("X-Member-Id") Long userId, @ModelAttribute ScheduleSearchDTO dto) {

        Page<ScheduleReadDTO> response = scheduleQueryService.getSchedules(dto, userId);

        return ResponseEntity.ok(ApiResponse.success(response));
    }


    // 기술자 본인의 예약 목록 조회
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<Page<ScheduleReadDTO>>> getMySchedules(@RequestHeader("X-Member-Id") Long userId, @ModelAttribute ScheduleSearchDTO dto) {

        Page<ScheduleReadDTO> response = scheduleQueryService.getMySchedules(dto, userId);


        return ResponseEntity.ok(ApiResponse.success(response));
    }


    // 예약 요청자 본인의 예약 목록 조회
    @GetMapping("/requested-by-me")
    public ResponseEntity<ApiResponse<Page<ScheduleReadDTO>>> getRequestedSchedules(@RequestHeader("X-Member-Id") Long userId, @ModelAttribute ScheduleSearchDTO dto) {

        Page<ScheduleReadDTO> response = scheduleQueryService.getRequestedSchedules(dto, userId);


        return ResponseEntity.ok(ApiResponse.success(response));
    }


    // 예약 상세 조회
    @GetMapping("/{scheduleId}")
    public ResponseEntity<ApiResponse<ScheduleReadDTO>> getSchedule(@RequestHeader("X-Member-Id") Long userId, @PathVariable Long scheduleId) {

        ScheduleReadDTO response = scheduleQueryService.getSchedule(scheduleId, userId);


        return ResponseEntity.ok(ApiResponse.success(response));
    }
}