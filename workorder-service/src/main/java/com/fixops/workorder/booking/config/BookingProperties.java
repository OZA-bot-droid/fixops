package com.fixops.workorder.booking.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;
import java.time.ZoneId;



@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "booking.holiday-api") // application.yml의 booking으로 시작하는 설정값들 매핑
public class BookingProperties { // 예약 정책 설정값 관리

    // 기술자 응답 기한
    @Positive
    private long technicianResponseHours = 24;


    // 예약 요청자 응답 기한
    @Positive
    private long requesterResponseHours = 12;


    // 전체 협의 유효기간
    @Positive
    private long negotiationExpiryHours = 48;


    // 방문 전 최소 준비시간
    @Positive
    private long minimumLeadHours = 1;


    // 만료 확인 주기
    @Positive
    private long expiryCheckIntervalMs = 60000;

    // 예약 업무 기준 시간대
    @NotNull
    private ZoneId timeZone = ZoneId.of("Asia/Seoul");


    // 업무 시작 시각
    @NotNull
    private LocalTime businessStartTime = LocalTime.of(9, 0);


    // 업무 종료 시각
    @NotNull
    private LocalTime businessEndTime = LocalTime.of(20, 0);


    // 후보 생성 간격
    @Positive
    private int slotIntervalMinutes = 30;


    // 최소 방문 소요 시간
    @Positive
    private int minimumDurationMinutes = 30;


    // 최대 방문 소요 시간
    @Positive
    private int maximumDurationMinutes = 480;


    // 최대 미래 예약 가능 일수
    @Positive
    private int maxAdvanceDays = 30;


    // 주말 방문 허용 여부
    private boolean allowWeekends = false;


    // 공휴일 방문 허용 여부
    private boolean allowHolidays = false;


    // 시간의 길이 또는 두 시점 사이의 시간 간격을 표현
    public Duration technicianResponseDuration() {

        // 24시간이라는 시간 길이를 나타내는 객체를 반환
        return Duration.ofHours(technicianResponseHours);
    }

    // 입주민 또는 관리자의 응답 유효기간을 Duration 객체로 반환
    public Duration requesterResponseDuration() {

        return Duration.ofHours(requesterResponseHours);
    }

    // 전체 예약 협의 유효기간을 Duration 객체로 반환
    public Duration negotiationExpiryDuration() {

        return Duration.ofHours(negotiationExpiryHours);
    }

    // 방문 전 최소 준비시간을 Duration 객체로 반환
    public Duration minimumLeadDuration() {

        return Duration.ofHours(minimumLeadHours);
    }

    // 시간 관련 검증
    @PostConstruct // Bean의 생성 및 의존성 주입이 완료된 이후 초기화 과정에서 자동으로 메서드를 실행
    public void validateTimeConfiguration() {

        if (timeZone == null || businessStartTime == null || businessEndTime == null

                || !businessStartTime.isBefore(businessEndTime)) {

            throw new IllegalStateException("업무 시간 설정이 올바르지 않습니다.");
        }


        if (slotIntervalMinutes <= 0 || minimumDurationMinutes <= 0 || maximumDurationMinutes < minimumDurationMinutes || maxAdvanceDays <= 0) {

            throw new IllegalStateException("방문 시간 정책 설정이 올바르지 않습니다.");
        }


        if (businessStartTime.getSecond() != 0 || businessStartTime.getNano() != 0 || businessEndTime.getSecond() != 0 || businessEndTime.getNano() != 0) {

            throw new IllegalStateException("업무 시간은 분 단위로 설정해야 합니다.");
        }


        long businessMinutes = Duration.between(businessStartTime, businessEndTime).toMinutes();


        if (minimumDurationMinutes % slotIntervalMinutes != 0

                || maximumDurationMinutes % slotIntervalMinutes != 0

                || businessMinutes % slotIntervalMinutes != 0

                || maximumDurationMinutes > businessMinutes) {

            throw new IllegalStateException("업무 시간과 방문 소요 시간 설정이 일치하지 않습니다.");
        }
    }
}