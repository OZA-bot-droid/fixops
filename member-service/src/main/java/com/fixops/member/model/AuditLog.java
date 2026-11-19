package com.fixops.member.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "audit_logs")
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "actor_id", nullable = false)
    public Long actorId;
    @Column(nullable = false)
    public String operation;
    @Column(name = "target_type", nullable = false)
    public String targetType;
    @Column(name = "target_id", nullable = false)
    public String targetId;
    @Column(name = "occurred_at", nullable = false)
    public Instant occurredAt = Instant.now();

    protected AuditLog() {
    }

    public AuditLog(Long actorId, String operation, String targetType, String targetId) {
        this.actorId = actorId;
        this.operation = operation;
        this.targetType = targetType;
        this.targetId = targetId;
    }
}
