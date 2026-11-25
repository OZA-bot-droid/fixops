package com.fixops.workorder.booking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class BookingClockConfig { // 현재 시각 공급자 등록

    @Bean
    public Clock bookingClock() {

        // 시스템 시계를 기반으로 현재 시각을 제공하며, 시간대를 UTC로 사용하는 Clock 객체를 생성
        return Clock.systemUTC();
    }
}