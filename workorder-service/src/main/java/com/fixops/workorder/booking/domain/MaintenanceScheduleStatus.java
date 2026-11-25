package com.fixops.workorder.booking.domain;


import java.util.Set;

public enum MaintenanceScheduleStatus {
    // 기술자 응답 대기
    PENDING,

    // 예약 요청자 응답 대기
    COUNTER_PROPOSED,

    // 예약 확정
    CONFIRMED,

    // 일정 협의 거절
    REJECTED,

    // 예약 철회 또는 취소
    CANCELLED,

    // 방문 완료
    COMPLETED,

    // 응답 또는 협의 기한 만료
    EXPIRED;


    // 현재 일정 협의 중인지 확인
    public boolean isNegotiating() {

        return this == PENDING || this == COUNTER_PROPOSED;
    }


    // 활성 예약인지 확인
    public boolean isActive() {

        return this == PENDING || this == COUNTER_PROPOSED || this == CONFIRMED;
    }


    // 기술자의 시간을 점유하는 상태인지 확인
    public boolean occupiesTime() {

        return this == COUNTER_PROPOSED || this == CONFIRMED;
    }


    // 종결된 예약인지 확인
    public boolean isTerminal() {

        return this == REJECTED || this == CANCELLED || this == COMPLETED || this == EXPIRED;
    }

    // 활성 예약 상태
    public static Set<MaintenanceScheduleStatus> activeStatuses() {

        return Set.of(PENDING, COUNTER_PROPOSED, CONFIRMED);
    }


    // 시간 점유 상태
    public static Set<MaintenanceScheduleStatus> occupyingStatuses() {

        return Set.of(COUNTER_PROPOSED, CONFIRMED);
    }


    // 일정 협의 진행 중인 상태
    public static Set<MaintenanceScheduleStatus> negotiatingStatuses() {

        return Set.of(PENDING, COUNTER_PROPOSED);
    }
}