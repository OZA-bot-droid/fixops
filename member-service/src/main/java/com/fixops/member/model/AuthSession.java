package com.fixops.member.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_sessions")
public class AuthSession {
    @Id
    public UUID id;
    @Column(name = "member_id", nullable = false)
    public Long memberId;
    @Column(name = "expires_at", nullable = false)
    public Instant expiresAt;
    @Column(nullable = false)
    public boolean revoked;
    @Column(name = "revoked_at")
    public Instant revokedAt;
    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();

    protected AuthSession() {
    }

    public AuthSession(UUID id, Long memberId, Instant expiresAt) {
        this.id = id;
        this.memberId = memberId;
        this.expiresAt = expiresAt;
    }
}
