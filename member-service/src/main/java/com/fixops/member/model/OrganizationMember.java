package com.fixops.member.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "organization_members", uniqueConstraints = @UniqueConstraint(columnNames = {"organization_id", "member_id"}))
public class OrganizationMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "organization_id", nullable = false)
    public Long organizationId;
    @Column(name = "member_id", nullable = false)
    public Long memberId;
    @Column(nullable = false)
    public String role;
    @Column(nullable = false)
    public String status = "ACTIVE";
    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();

    protected OrganizationMember() {
    }

    public OrganizationMember(Long organizationId, Long memberId, String role) {
        this.organizationId = organizationId;
        this.memberId = memberId;
        this.role = role;
    }
}
