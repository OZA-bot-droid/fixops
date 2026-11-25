package com.fixops.workorder.booking.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "booking_idempotency")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookingIdempotency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    // 작업지시 ID
    @Column(name = "work_order_id", nullable = false)
    private Long workOrderId;


    // 조직 ID
    @Column(name = "organization_id", nullable = false)
    private Long organizationId;


    // 요청 사용자 ID
    @Column(name = "actor_id", nullable = false)
    private Long actorId;


    // 요청 작업 종류
    @Column(nullable = false, length = 50)
    private String operation;


    // 요청 고유 키
    @Column(name = "request_key", nullable = false, length = 200)
    private String requestKey;


    // 요청 본문 SHA-256 해시
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;


    // 최초 HTTP 응답 상태 코드
    @Column(name = "response_status")
    private Integer responseStatus;


    // 최초 응답 JSON
    // JSONB 컬럼을 Hibernate에서 JSON 타입으로 처리하도록 설정
    // 예약 응답을 JSON 문자열로 변환하여 저장하고, 동일한 요청이 다시 발생하면 저장된 JSON 응답을 반환
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_body", columnDefinition = "jsonb")
    private String responseBody;


    // 요청 키 등록 시각
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;


    // 정상 처리 결과 저장
    public void saveResponse(int responseStatus, String responseBody) {

        if (hasResponse()) {
            throw new IllegalStateException("이미 저장된 멱등성 응답입니다.");
        }

        if (responseBody == null || responseBody.isBlank()) {
            throw new IllegalArgumentException("응답 본문이 필요합니다.");
        }

        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
    }


    // 응답 저장 완료 여부
    public boolean hasResponse() {

        return responseStatus != null && responseBody != null;
    }
}