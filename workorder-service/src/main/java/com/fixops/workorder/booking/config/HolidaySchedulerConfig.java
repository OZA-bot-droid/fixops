package com.fixops.workorder.booking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;


@Configuration
public class HolidaySchedulerConfig {

    // 기존 예약 자동 만료용 Scheduler
    @Bean(name = "taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {

        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();

        scheduler.setPoolSize(1);

        scheduler.setThreadNamePrefix("booking-scheduler-");

        return scheduler;
    }


    // 공휴일 데이터 동기화 전용 Scheduler
    @Bean(name = "holidayTaskScheduler")
    public ThreadPoolTaskScheduler holidayTaskScheduler() {

        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();

        scheduler.setPoolSize(1);

        scheduler.setThreadNamePrefix("holiday-scheduler-");

        return scheduler;
    }
}