package com.fixops.workorder.booking.holiday.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;


@Entity
@Table(name = "holiday_sync_month")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HolidaySyncMonth {

    // 공휴일 수집 대상 월의 첫 번째 날짜
    @Id
    @Column(name = "target_month", nullable = false)
    private LocalDate targetMonth;


    // 마지막 정상 수집 기준 시각
    @Column(name = "last_success_at", nullable = false)
    private Instant lastSuccessAt;


    // 해당 월에 저장된 공휴일 날짜 개수
    @Column(name = "holiday_count", nullable = false)
    private int holidayCount;

}