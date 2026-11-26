package com.fixops.member.repo;

import com.fixops.member.model.RefreshSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RefreshSessionRepository extends JpaRepository<RefreshSession, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshSession> findByTokenHash(String tokenHash);

    // 호환용 기존 조회 메서드 유지.
    @Query("select s from RefreshSession s where s.tokenHash = :tokenHash")
    Optional<RefreshSession> findUnlockedByTokenHash(@Param("tokenHash") String tokenHash);

    // 세션 Lock 이전에 엔티티를 영속성 컨텍스트에 올리지 않는다.
    // 경쟁 요청에서 갱신 전 revoked 값이 캐시되는 문제를 피한다.
    @Query("select s.authSessionId from RefreshSession s where s.tokenHash = :tokenHash")
    Optional<UUID> findSessionIdByTokenHash(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("update RefreshSession s set s.revoked = true where s.authSessionId = :sid and s.revoked = false")
    int revokeByAuthSessionId(@Param("sid") UUID sid);
}
