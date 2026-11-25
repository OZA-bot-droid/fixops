package com.fixops.workorder.booking.controller;

import com.fixops.workorder.booking.dto.ScheduleProposalRejectDTO;
import com.fixops.workorder.booking.dto.ScheduleProposalSaveDTO;
import com.fixops.workorder.booking.service.BookingIdempotencyService;
import com.fixops.workorder.booking.service.ScheduleProposalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/schedules/{scheduleId}/proposals")
@RequiredArgsConstructor
public class ScheduleProposalController {

    private final ScheduleProposalService proposalService;

    // 1. 대체 시간 제안 및 재제안
    @PostMapping
    public ResponseEntity<String> createProposal(@RequestHeader("X-Member-Id") Long userId, @RequestHeader("Idempotency-Key") String requestKey,
                                                 @PathVariable Long scheduleId, @Valid @RequestBody ScheduleProposalSaveDTO dto) {

        BookingIdempotencyService.StoredResponse response = proposalService.createProposal(scheduleId, dto, userId, requestKey);

        return toResponseEntity(response);
    }


    // 2. 현재 시간 제안 수락
    @PostMapping("/{proposalId}/accept")
    public ResponseEntity<String> acceptProposal(@RequestHeader("X-Member-Id") Long userId, @RequestHeader("Idempotency-Key") String requestKey,
                                                 @PathVariable Long scheduleId, @PathVariable Long proposalId) {

        BookingIdempotencyService.StoredResponse response = proposalService.acceptProposal(scheduleId, proposalId, userId, requestKey);

        return toResponseEntity(response);
    }


    // 3. 현재 시간 제안 거절
    @PostMapping("/{proposalId}/reject")
    public ResponseEntity<String> rejectProposal(@RequestHeader("X-Member-Id") Long userId, @RequestHeader("Idempotency-Key") String requestKey,
                                                 @PathVariable Long scheduleId, @PathVariable Long proposalId, @Valid @RequestBody ScheduleProposalRejectDTO dto) {

        BookingIdempotencyService.StoredResponse response = proposalService.rejectProposal(scheduleId, proposalId, dto, userId, requestKey);

        return toResponseEntity(response);
    }


    // 4. 공통 HTTP 응답 변환
    private ResponseEntity<String> toResponseEntity(BookingIdempotencyService.StoredResponse response) {

        return ResponseEntity.status(response.status()).contentType(MediaType.APPLICATION_JSON).body(response.body());
    }
}