package com.fixops.workorder.booking.dto;

import com.fixops.workorder.booking.domain.MaintenanceScheduleStatus;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;

@Getter
@Setter
public class ScheduleSearchDTO { // 예약 검색 파라미터 dto

    // 필수 검색 범위
    private Long organizationId;

    // 선택 검색 범위
    private Long complexId;

    private Long locationId;

    private Long assetId;

    // 관리자 조회에서만 사용
    private Long technicianId;

    // 예약 상태
    private MaintenanceScheduleStatus status;

    // 방문 일정 조회 시작 시각
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant fromAt;

    // 방문 일정 조회 종료 시각
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant toAt;

    // 페이지네이션
    private int page = 0;

    private int size = 10;

    // 기본 정렬: 방문 시작 시각 오름차순
    private String sort = "startAt,asc";
}