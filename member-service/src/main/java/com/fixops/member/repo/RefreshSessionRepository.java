package com.fixops.member.repo;
import com.fixops.member.model.RefreshSession;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RefreshSessionRepository extends JpaRepository<RefreshSession,java.util.UUID> { @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 java.util.Optional<RefreshSession> findByTokenHash(String tokenHash); }
