package com.fixops.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/**
 * 부품의 현재 재고. 부품 1개당 1행(part_id UNIQUE).
 * 수량 변경은 입고·사용을 통해서만 이뤄지며, 차감은 StockDeductionService가 SQL로 직접 처리한다.
 */
@Entity
@Table(name = "stock_balance")
public class StockBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

//    Lazy : 필요할 때 가져온다
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "part_id", nullable = false, unique = true)
    private Part part;

    @Column(nullable = false)
    private Integer quantity;

    /** 동시 수정 감지용. 차감 SQL에서도 같이 증가시킨다. */
    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StockBalance() {
    }

    private StockBalance(Part part, Integer quantity) {
        this.part = part;
        this.quantity = quantity;
        this.updatedAt = Instant.now();
    }

    /** 부품 등록과 같은 트랜잭션에서 수량 0인 잔액 행을 만든다. */
    public static StockBalance zeroFor(Part part) {
        return new StockBalance(part, 0);
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Part getPart() {
        return part;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
