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

    // PK. IDENTITY: 번호는 저장할 때 DB(BIGSERIAL)가 붙인다.
    // 그래서 새로 만든 객체는 id 가 null 이고, JPA 는 이것을 보고 INSERT 를 한다 (id 가 있으면 UPDATE).
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 어느 단지(조직)의 부품인지. 모든 조회·수정 조건에 함께 들어가서, 남의 단지 부품을 볼 수 없게 한다.
    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    // 부품 코드 (예: BOLT-M8). 같은 조직 안에서 유일(uq_part_org_sku). 등록 후에는 바꾸지 않는다.
    @Column(nullable = false, length = 64)
    private String sku;

    @Column(nullable = false, length = 200)
    private String name;

    // 분류 (예: 체결, 배관). 비워 둘 수 있다.
    @Column(length = 64)
    private String category;

    // 단위 (EA, M, L 등). 등록할 때 비우면 EA(개).
    @Column(nullable = false, length = 16)
    private String unit;

    // 안전재고. 현재 수량이 이 값보다 적으면 "부족 재고"로 본다 (목록의 lowStockOnly).
    @Column(name = "safety_stock", nullable = false)
    private Integer safetyStock;

    // EnumType.STRING: DB 에 0, 1 같은 숫자가 아니라 'ACTIVE' 같은 글자로 저장한다.
    // 숫자(ORDINAL)로 저장하면 enum 의 순서를 바꾸는 순간 이미 저장된 데이터의 뜻이 바뀐다.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PartStatus status;

    // updatable = false: 처음 저장한 뒤에는 UPDATE 문에 넣지 않는다 (생성 시각은 바뀌면 안 됨).
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** JPA가 객체를 만들 때 쓰는 기본 생성자. 직접 쓰지 않는다. */
    protected Part() {
    }

    // 새 부품의 기본값 규칙은 이 한 곳에만 둔다 (unit 없으면 EA, 안전재고 없으면 0, 상태는 ACTIVE).
    // private: 밖에서는 아래 create() 로만 만들 수 있다.
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

//  수정에 관련 메서드
    /**
     * 부품 정보 수정. null 로 넘긴 항목은 바꾸지 않는다 (PATCH: 보낸 것만 바꿈).
     * sku, organizationId, status, 재고 수량은 여기서 바꿀 수 없다.
     * 값만 바꾸면 트랜잭션이 끝날 때 JPA 가 알아서 UPDATE 한다 (변경 감지).
     */
    public void changeInfo(String name, String category, String unit, Integer safetyStock) {
        if (name != null) {
            this.name = name;
        }
        if (category != null) {
            this.category = category;
        }
        if (unit != null) {
            this.unit = unit;
        }
        if (safetyStock != null) {
            this.safetyStock = safetyStock;
        }
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

    // ----- getter 만 있고 setter 는 없다 -----
    // 값은 create()(처음 만들 때)와 changeInfo()(수정할 때)로만 바뀐다.
    // 아무 곳에서나 setXxx() 로 바꾸면 검증 없이 값이 바뀌고, 어디서 바꿨는지 찾기 어렵다.

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
