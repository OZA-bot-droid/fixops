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
@Table(name = "public_holiday")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicHoliday {

    // 실제 공휴일 날짜
    @Id
    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;


    // 공휴일 명칭
    @Column(name = "holiday_name", nullable = false, columnDefinition = "TEXT")
    private String holidayName;


    // 해당 공휴일 데이터 수집 기준 시각
    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

}