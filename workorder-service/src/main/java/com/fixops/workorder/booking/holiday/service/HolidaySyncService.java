package com.fixops.workorder.booking.holiday.service;

import com.fixops.workorder.booking.config.BookingProperties;
import com.fixops.workorder.booking.config.HolidayApiProperties;

import com.fixops.workorder.booking.holiday.client.HolidayApiClient;
import com.fixops.workorder.booking.holiday.dto.PublicHolidayDTO;
import com.fixops.workorder.booking.holiday.repository.HolidaySyncMonthRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

import java.util.List;
import java.util.Optional;


@Slf4j
@Service
@RequiredArgsConstructor
public class HolidaySyncService {

    private final HolidayApiClient holidayApiClient;

    private final HolidaySyncMonthRepository holidaySyncMonthRepository;

    private final HolidayPersistenceService persistenceService;

    private final BookingProperties bookingProperties;

    private final HolidayApiProperties holidayApiProperties;

    private final Clock clock;


    // =====================================
    // 필요한 월별 공휴일 데이터 동기화
    // =====================================

    public void syncRequiredMonths() {

        // 공휴일 방문 제한을 사용하지 않는 경우
        // 외부 공휴일 API 호출 생략
        if (bookingProperties.isAllowHolidays()) {

            return;
        }


        Instant now = clock.instant();


        // 업무 기준 시간대로 현재 날짜 계산
        LocalDate today = now

                .atZone(bookingProperties.getTimeZone())

                .toLocalDate();


        // 최대 예약 가능 날짜 및 사전 수집 범위 계산
        LocalDate lastDate = today

                .plusDays(bookingProperties.getMaxAdvanceDays())

                .plusDays(holidayApiProperties.getPrefetchDays());


        YearMonth month = YearMonth.from(today);

        YearMonth lastMonth = YearMonth.from(lastDate);


        // 현재 월부터 마지막 필요 월까지 순차 처리
        while (!month.isAfter(lastMonth)) {

            try {

                syncMonthIfRequired(month);

            } catch (Exception ex) {

                // 인증키가 로그에 노출되지 않도록
                // 원본 API 요청 URL은 기록하지 않음
                log.warn(
                        "공휴일 동기화 실패: month={}, type={}, message={}",
                        month,
                        ex.getClass().getSimpleName(),
                        ex.getMessage()
                );
            }


            month = month.plusMonths(1);
        }
    }


    // =====================================
    // 월별 공휴일 갱신 필요 여부 확인
    // =====================================

    private void syncMonthIfRequired(YearMonth month) {

        Instant now = clock.instant()

                .truncatedTo(ChronoUnit.MICROS);


        // 해당 월의 마지막 정상 수집 기준 시각 조회
        Optional<Instant> lastSuccess =

                holidaySyncMonthRepository.findLastSuccessAt(

                        month.atDay(1));


        // 정상 데이터가 아직 갱신 주기 이내이면
        // 불필요한 외부 API 호출 방지
        if (lastSuccess.isPresent()

                && now.isBefore(

                lastSuccess.get().plus(

                        holidayApiProperties.getRefreshAfter()))) {

            return;
        }


        // 외부 API 호출
        // DB 저장 트랜잭션 밖에서 실행
        List<PublicHolidayDTO> holidays =

                holidayApiClient.fetchHolidays(month);


        // 정상 응답에 대해서만
        // 독립적인 DB 저장 트랜잭션 실행
        boolean saved = persistenceService.replaceMonth(

                month,

                holidays,

                now);


        if (saved) {

            log.info(

                    "공휴일 동기화 완료: month={}, count={}",

                    month,

                    holidays.size());

        } else {

            log.info(

                    "공휴일 동기화 생략: 최신 데이터 존재, month={}",

                    month);
        }
    }
}