package com.fixops.workorder.booking.config;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.extern.slf4j.Slf4j;
import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Slf4j
@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "booking.holiday-api")
public class HolidayApiProperties {

    // 공공데이터 API 기본 주소
    @NotBlank
    private String baseUrl;


    // 공공데이터포털 인증키
    @NotBlank
    private String serviceKey;


    // 연결 제한 시간
    @NotNull
    private Duration connectTimeout = Duration.ofSeconds(3);


    // 응답 읽기 제한 시간
    @NotNull
    private Duration readTimeout = Duration.ofSeconds(5);


    // 정상 수집 이후 갱신 기준 시간
    @NotNull
    private Duration refreshAfter = Duration.ofHours(24);


    // API 장애 시 기존 데이터 사용 가능 기간
    @NotNull
    private Duration maxStaleAge = Duration.ofDays(7);


    // 최초 수집 실행 지연 시간
    @PositiveOrZero
    private long initialDelayMs = 1000;


    // 동기화 필요 여부 확인 주기
    @Positive
    private long syncIntervalMs = 3600000;


    // 예약 가능 범위 이후 사전 수집 일수
    @PositiveOrZero
    private int prefetchDays = 2;


    @PostConstruct
    public void validateConfiguration() {

        log.info(
                "Holiday API baseUrl: {}",
                baseUrl
        );

        log.info(
                "Holiday API key configured: {}",
                serviceKey != null && !serviceKey.isBlank()
        );

        log.info(
                "Holiday API key length: {}",
                serviceKey == null ? 0 : serviceKey.length()
        );

        log.info(
                "Holiday API key URL encoded: {}",
                serviceKey != null && serviceKey.matches(
                        ".*%[0-9a-fA-F]{2}.*"
                )
        );

        log.info(
                "Holiday API key unresolved placeholder: {}",
                serviceKey != null && serviceKey.contains("${")
        );

        log.info(
                "Holiday API key surrounding whitespace: {}",
                serviceKey != null
                        && !serviceKey.equals(serviceKey.strip())
        );
        if (connectTimeout == null || connectTimeout.isZero() || connectTimeout.isNegative()

                || readTimeout == null || readTimeout.isZero() || readTimeout.isNegative()

                || refreshAfter == null || refreshAfter.isZero() || refreshAfter.isNegative()

                || maxStaleAge == null || maxStaleAge.compareTo(refreshAfter) < 0) {

            throw new IllegalStateException("공휴일 API 설정이 올바르지 않습니다.");
        }
    }
}