package com.fixops.workorder.booking.config;

import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "booking") // application.yml의 booking으로 시작하는 설정값들 매핑
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
}