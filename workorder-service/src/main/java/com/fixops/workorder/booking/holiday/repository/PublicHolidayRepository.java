package com.fixops.workorder.booking.holiday.repository;

import com.fixops.workorder.booking.holiday.domain.PublicHoliday;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;


public interface PublicHolidayRepository extends JpaRepository<PublicHoliday, LocalDate> {

    // 특정 기간의 공휴일 목록 조회
    List<PublicHoliday> findByHolidayDateGreaterThanEqualAndHolidayDateLessThanOrderByHolidayDateAsc(LocalDate from, LocalDate to);


    // 특정 월의 기존 공휴일 데이터 일괄 삭제
    //    @Modifying	조회가 아닌 INSERT·UPDATE·DELETE 쿼리임을 지정
    //    flushAutomatically	쿼리 실행 전에 영속성 컨텍스트의 변경사항을 DB에 반영
    //    clearAutomatically	쿼리 실행 후 영속성 컨텍스트 초기화
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            DELETE FROM PublicHoliday h
            WHERE h.holidayDate >= :from
              AND h.holidayDate < :to
            """)
    int deleteMonth(@Param("from") LocalDate from, @Param("to") LocalDate to);

}