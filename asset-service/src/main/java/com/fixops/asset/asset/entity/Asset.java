package com.fixops.asset.asset.entity;

import com.fixops.asset.location.entity.Location;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Location과 분류를 참조하는 Asset 서비스 소유 설비. */
@Entity
@Table(name = "asset")
public class Asset {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull @Positive
    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Positive
    @Column(name = "complex_id")
    private Long complexId;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private AssetCategory category;

    @NotBlank @Size(max = 100)
    @Column(name = "asset_code", nullable = false, length = 100)
    private String assetCode;

    @NotBlank @Size(max = 200)
    @Column(nullable = false, length = 200)
    private String name;

    @Size(max = 2000)
    @Column(length = 2000)
    private String description;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssetStatus status = AssetStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Asset() {}

    public Asset(Long organizationId, Long complexId, Location location, AssetCategory category,
                 String assetCode, String name, String description) {
        this.organizationId = organizationId;
        this.complexId = complexId;
        this.location = location;
        this.category = category;
        this.assetCode = assetCode;
        this.name = name;
        this.description = description;
    }

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public Long getComplexId() { return complexId; }
    public Long getLocationId() { return location.getId(); }
    public Long getCategoryId() { return category.getId(); }
    public String getCategoryName() { return category.getName(); }
    public String getAssetCode() { return assetCode; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public AssetStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(String name, String description, AssetStatus status) {
        this.name = name;
        this.description = description;
        this.status = status;
    }
}
