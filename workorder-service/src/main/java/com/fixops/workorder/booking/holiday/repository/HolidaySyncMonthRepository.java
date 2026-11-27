package com.fixops.workorder.booking.holiday.repository;

import com.fixops.workorder.booking.holiday.domain.HolidaySyncMonth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;


public interface HolidaySyncMonthRepository extends JpaRepository<HolidaySyncMonth, LocalDate> {


    // =====================================
    // 필요한 조회 결과만 가져오기 위한 공휴일 판별용 조회 Projection
    // =====================================

    interface CalendarState {
        Instant getLastSuccessAt();
        boolean getHoliday();
    }


    // =====================================
    // 월별 마지막 정상 수집 시각 조회
    // =====================================

    @Query("""
            SELECT m.lastSuccessAt
            FROM HolidaySyncMonth m
            WHERE m.targetMonth = :targetMonth
            """)
    Optional<Instant> findLastSuccessAt(

            @Param("targetMonth") LocalDate targetMonth);


    // =====================================
    // 공휴일 여부와 월별 수집 시각 동시 조회
    // =====================================

    @Query("""
            SELECT
                m.lastSuccessAt AS lastSuccessAt,
            
                CASE
                    WHEN EXISTS (
                        SELECT h.holidayDate
                        FROM PublicHoliday h
                        WHERE h.holidayDate = :date
                    )
                    THEN true
                    ELSE false
                END AS holiday
            
            FROM HolidaySyncMonth m
            
            WHERE m.targetMonth = :targetMonth
            """)
    Optional<CalendarState> findCalendarState(

            @Param("targetMonth") LocalDate targetMonth,

            @Param("date") LocalDate date);


    // =====================================
    // 월별 수집 정보 INSERT 또는 UPDATE
    // =====================================

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO holiday_sync_month (
            
                target_month,
            
                last_success_at,
            
                holiday_count
            
            )
            VALUES (
            
                :targetMonth,
            
                :fetchedAt,
            
                :holidayCount
            
            )
            
            ON CONFLICT (target_month)
            
            DO UPDATE SET
            
                last_success_at =
                    EXCLUDED.last_success_at,
            
                holiday_count =
                    EXCLUDED.holiday_count
            
            WHERE holiday_sync_month.last_success_at
                < EXCLUDED.last_success_at
            """,

            nativeQuery = true)
    int upsertSyncMonth(

            @Param("targetMonth") LocalDate targetMonth,

            @Param("fetchedAt") Instant fetchedAt,

            @Param("holidayCount") int holidayCount);

}