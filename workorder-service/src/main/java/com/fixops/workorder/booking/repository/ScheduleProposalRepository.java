package com.fixops.workorder.booking.repository;

import com.fixops.workorder.booking.domain.ScheduleProposal;
import com.fixops.workorder.booking.domain.ScheduleProposalStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ScheduleProposalRepository extends JpaRepository<ScheduleProposal, Long> {

    // 전체 일정 협의 이력 조회
    List<ScheduleProposal> findByScheduleIdOrderByCreatedAtAscIdAsc(Long scheduleId);

    // 예약 ID 및 Proposal ID 기준 조회
    Optional<ScheduleProposal> findByIdAndScheduleId(Long proposalId, Long scheduleId);

    // 현재 활성 Proposal 조회
    Optional<ScheduleProposal> findByScheduleIdAndStatus(Long scheduleId, ScheduleProposalStatus status);


    // Proposal 비관적 락 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT p
            FROM ScheduleProposal p
            WHERE p.id = :proposalId
              AND p.scheduleId = :scheduleId
            """)
    Optional<ScheduleProposal> findByIdForUpdate(@Param("proposalId") Long proposalId, @Param("scheduleId") Long scheduleId);


    // 활성 Proposal 비관적 락 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT p
            FROM ScheduleProposal p
            WHERE p.scheduleId = :scheduleId
              AND p.status = :status
            """)
    Optional<ScheduleProposal> findActiveProposalForUpdate(@Param("scheduleId") Long scheduleId, @Param("status") ScheduleProposalStatus status);


    // 개별 응답 기한 만료 대상 예약 ID 조회
    @Query("""
            SELECT p.scheduleId
            FROM ScheduleProposal p
            WHERE p.status = :status
              AND p.expiresAt <= :now
            ORDER BY p.expiresAt ASC, p.id ASC
            """)
    List<Long> findExpiredProposalScheduleIds(@Param("status") ScheduleProposalStatus status, @Param("now") Instant now, Pageable pageable);
}