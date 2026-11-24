package com.fixops.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * 부품 기준 정보. 수량은 여기 두지 않고 StockBalance가 가진다.
 * 같은 조직 안에서 sku는 중복될 수 없다.
 */
@Entity
@Table(name = "part",
        uniqueConstraints = @UniqueConstraint(name = "uq_part_org_sku",
                columnNames = {"organization_id", "sku"}))
public class Part {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(nullable = false, length = 64)
    private String sku;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 64)
    private String category;

    @Column(nullable = false, length = 16)
    private String unit;

    @Column(name = "safety_stock", nullable = false)
    private Integer safetyStock;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PartStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** JPA가 객체를 만들 때 쓰는 기본 생성자. 직접 쓰지 않는다. */
    protected Part() {
    }

    private Part(Long organizationId, String sku, String name, String category,
                 String unit, Integer safetyStock) {
        this.organizationId = organizationId;
        this.sku = sku;
        this.name = name;
        this.category = category;
        this.unit = (unit == null || unit.isBlank()) ? "EA" : unit;
        this.safetyStock = (safetyStock == null) ? 0 : safetyStock;
        this.status = PartStatus.ACTIVE;
    }

//  생성에 관련 메서드
    public static Part create(Long organizationId, String sku, String name, String category,
                              String unit, Integer safetyStock) {
        return new Part(organizationId, sku, name, category, unit, safetyStock);
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getOrganizationId() {
        return organizationId;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public String getUnit() {
        return unit;
    }

    public Integer getSafetyStock() {
        return safetyStock;
    }

    public PartStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
