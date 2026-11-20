package com.fixops.asset.location.entity;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/** Asset 서비스 소유 위치 노드. 조직·단지 ID는 다른 서비스 DB의 논리 참조다. */
@Entity
@Table(name = "location")
public class Location {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Positive
    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Positive
    @Column(name = "complex_id")
    private Long complexId;

    // 같은 테이블 안에서만 부모를 참조하므로 서비스 간 FK가 아니다.
    @ManyToOne
    @JoinColumn(name = "parent_id")
    private Location parent;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "location_type", nullable = false, length = 40)
    private LocationType locationType;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String code;

    @NotBlank
    @Size(max = 200)
    @Column(nullable = false, length = 200)
    private String name;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LocationStatus status = LocationStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Location() {}

    public Location(Long organizationId, Long complexId, Location parent,
                    LocationType locationType, String code, String name) {
        this.organizationId = organizationId;
        this.complexId = complexId;
        this.parent = parent;
        this.locationType = locationType;
        this.code = code;
        this.name = name;
    }

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public Long getComplexId() { return complexId; }
    public Location getParent() { return parent; }
    public Long getParentId() { return parent == null ? null : parent.getId(); }
    public LocationType getLocationType() { return locationType; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public LocationStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    /** 위치 식별자와 표시명을 정정하거나 이력 보존을 위해 비활성화한다. */
    public void update(String code, String name, LocationStatus status) {
        this.code = code;
        this.name = name;
        this.status = status;
    }
}
