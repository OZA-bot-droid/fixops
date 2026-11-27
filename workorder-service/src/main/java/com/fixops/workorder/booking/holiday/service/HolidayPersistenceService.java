package com.fixops.workorder.booking.holiday.service;

import com.fixops.workorder.booking.holiday.domain.PublicHoliday;
import com.fixops.workorder.booking.holiday.dto.PublicHolidayDTO;

import com.fixops.workorder.booking.holiday.repository.PublicHolidayRepository;
import com.fixops.workorder.booking.holiday.repository.HolidaySyncMonthRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

import java.util.HashSet;
import java.util.List;
import java.util.Set;


@Service
@RequiredArgsConstructor
public class HolidayPersistenceService {

    private final PublicHolidayRepository publicHolidayRepository;

    private final HolidaySyncMonthRepository holidaySyncMonthRepository;


    // =====================================
    // 특정 월의 공휴일 데이터 전체 교체
    // =====================================

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean replaceMonth(

            YearMonth month,

            List<PublicHolidayDTO> holidays,

            Instant fetchedAt

    ) {

        // 1. 외부 API에서 전달받은 데이터 검증
        validateHolidayData(

                month,

                holidays,

                fetchedAt);


        // PostgreSQL의 마이크로초 정밀도에 맞춤
        Instant syncedAt = fetchedAt.truncatedTo(ChronoUnit.MICROS);


        // 2. 해당 월의 시작일 및 다음 달 시작일 계산
        LocalDate targetMonth = month.atDay(1);

        LocalDate nextMonth = month.plusMonths(1).atDay(1);


        // 3. DTO를 Entity로 변환
        List<PublicHoliday> entities = holidays.stream()

                .map(holiday -> PublicHoliday.builder()

                        .holidayDate(holiday.holidayDate())

                        .holidayName(holiday.holidayName())

                        .syncedAt(syncedAt)

                        .build())

                .toList();


        // 4. 월별 정상 수집 상태 INSERT 또는 UPDATE
        int updatedRows = holidaySyncMonthRepository.upsertSyncMonth(

                targetMonth,

                syncedAt,

                entities.size());


        // 5. 이미 더 최신 데이터가 존재하면 갱신 생략
        if (updatedRows == 0) {

            return false;
        }


        // 6. 해당 월의 기존 공휴일 데이터 삭제
        publicHolidayRepository.deleteMonth(

                targetMonth,

                nextMonth);


        // 7. 신규 공휴일 데이터 일괄 저장
        if (!entities.isEmpty()) {

            publicHolidayRepository.saveAllAndFlush(entities);
        }


        return true;
    }


    // =====================================
    // 저장할 공휴일 데이터 검증
    // =====================================

    private void validateHolidayData(

            YearMonth month,

            List<PublicHolidayDTO> holidays,

            Instant fetchedAt

    ) {

        if (month == null

                || holidays == null

                || fetchedAt == null) {

            throw new IllegalArgumentException("공휴일 저장 데이터가 올바르지 않습니다.");
        }


        Set<LocalDate> uniqueDates = new HashSet<>();


        for (PublicHolidayDTO holiday : holidays) {

            if (holiday == null

                    || holiday.holidayDate() == null

                    || holiday.holidayName() == null

                    || holiday.holidayName().isBlank()) {

                throw new IllegalArgumentException("공휴일 필수 데이터가 누락되었습니다.");
            }


            // 수집 대상 월에 해당하는 날짜인지 확인
            if (!YearMonth.from(holiday.holidayDate()).equals(month)) {

                throw new IllegalArgumentException("공휴일 데이터가 수집 대상 월을 벗어났습니다.");
            }


            // 동일 날짜 중복 저장 방지
            if (!uniqueDates.add(holiday.holidayDate())) {

                throw new IllegalArgumentException("중복된 공휴일 날짜가 존재합니다.");
            }
        }
    }
}