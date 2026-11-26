package com.fixops.member.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_sessions")
public class RefreshSession {
    @Id
    public UUID id;
    @Column(name = "auth_session_id", nullable = false)
    public UUID authSessionId;
    @Column(name = "member_id", nullable = false)
    public Long memberId;
    @Column(name = "token_hash", nullable = false, unique = true)
    public String tokenHash;
    @Column(name = "expires_at", nullable = false)
    public Instant expiresAt;
    @Column(nullable = false)
    public boolean revoked = false;
    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();

    protected RefreshSession() {
    }

    public RefreshSession(UUID id, UUID authSessionId, Long memberId, String tokenHash, Instant expiresAt) {
        this.id = id;
        this.authSessionId = authSessionId;
        this.memberId = memberId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }
}
