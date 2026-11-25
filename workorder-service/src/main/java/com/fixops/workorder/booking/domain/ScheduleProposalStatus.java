package com.fixops.workorder.booking.domain;


public enum ScheduleProposalStatus {
    // 상대방 응답 대기
    PENDING,

    // 제안 수락
    ACCEPTED,

    // 제안 거절
    REJECTED,

    // 새로운 제안으로 대체
    SUPERSEDED,

    // 예약 철회 또는 취소
    CANCELLED,

    // 개별 응답 기한 만료
    EXPIRED;


    // 상대방 응답 대기 상태인지 확인
    public boolean isPending() {

        return this == PENDING;
    }


    // 종료된 제안인지 확인
    public boolean isTerminal() {

        return this != PENDING;
    }
}