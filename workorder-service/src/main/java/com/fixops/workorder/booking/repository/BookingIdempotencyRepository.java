package com.fixops.workorder.booking.repository;

import com.fixops.workorder.booking.domain.BookingIdempotency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface BookingIdempotencyRepository extends JpaRepository<BookingIdempotency, Long> {

    // 기존 요청 키 조회
    Optional<BookingIdempotency> findByOrganizationIdAndActorIdAndOperationAndRequestKey(Long organizationId, Long actorId, String operation, String requestKey);


    // 신규 요청 키 등록
    // ON CONFLICT DO NOTHING으로 다른 트랜잭션에서 처리 중이라면 해당 트랜잭션의 결과가 결정될 때까지 INSERT를 대기시킴
    @Modifying
    @Query(value = """
            INSERT INTO booking_idempotency (
                work_order_id,
                organization_id,
                actor_id,
                operation,
                request_key,
                request_hash,
                created_at
            )
            VALUES (
                :workOrderId,
                :organizationId,
                :actorId,
                :operation,
                :requestKey,
                :requestHash,
                :createdAt
            )
            ON CONFLICT (
                organization_id,
                actor_id,
                operation,
                request_key
            )
            DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("workOrderId") Long workOrderId,
                        @Param("organizationId") Long organizationId,
                        @Param("actorId") Long actorId,
                        @Param("operation") String operation,
                        @Param("requestKey") String requestKey,
                        @Param("requestHash") String requestHash,
                        @Param("createdAt") Instant createdAt);
}