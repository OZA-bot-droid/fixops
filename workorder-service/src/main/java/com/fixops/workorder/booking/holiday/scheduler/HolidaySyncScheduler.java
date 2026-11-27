package com.fixops.workorder.booking.holiday.scheduler;

import com.fixops.workorder.booking.holiday.service.HolidaySyncService;

import lombok.RequiredArgsConstructor;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
public class HolidaySyncScheduler {

    private final HolidaySyncService holidaySyncService;


    @Scheduled(scheduler = "holidayTaskScheduler",
            initialDelayString = "${booking.holiday-api.initial-delay-ms:1000}",
            fixedDelayString = "${booking.holiday-api.sync-interval-ms:3600000}")
    public void synchronizeHolidays() {

        holidaySyncService.syncRequiredMonths();
    }
}