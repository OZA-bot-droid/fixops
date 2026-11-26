package com.fixops.workorder.booking.controller;

import com.fixops.workorder.booking.dto.ScheduleSaveDTO;
import com.fixops.workorder.booking.service.BookingIdempotencyService;
import com.fixops.workorder.booking.service.MaintenanceScheduleService;
import com.fixops.workorder.booking.dto.ScheduleCancelDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/schedules")
@RequiredArgsConstructor
public class MaintenanceScheduleController {

    private final MaintenanceScheduleService scheduleService;


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
}