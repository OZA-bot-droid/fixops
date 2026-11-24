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

    @Query("select s from RefreshSession s where s.tokenHash = :tokenHash")
    Optional<RefreshSession> findUnlockedByTokenHash(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("update RefreshSession s set s.revoked = true where s.authSessionId = :sid and s.revoked = false")
    int revokeByAuthSessionId(@Param("sid") UUID sid);
}
