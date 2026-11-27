package com.fixops.workorder.booking.holiday.dto;

import java.time.LocalDate;


public record PublicHolidayDTO(LocalDate holidayDate, String holidayName) {
}