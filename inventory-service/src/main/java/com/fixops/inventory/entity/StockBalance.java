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
 * 수량 변경은 입고·사용을 통해서만 이뤄지며, 입고·차감은 StockService가 SQL로 직접 처리한다.
 */
@Entity
@Table(name = "stock_balance")
public class StockBalance {

    // PK. 번호는 DB 가 붙인다 (Part 와 같은 방식).
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 부품 1개 ↔ 재고 행 1개 (1:1). part_id 에 UNIQUE 가 있어서 한 부품에 재고 행이 두 개 생길 수 없다.
    // optional = false: 재고 행은 반드시 부품이 있어야 한다.
//    Lazy : 필요할 때 가져온다
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "part_id", nullable = false, unique = true)
    private Part part;

    // 현재 수량. 0 이상(DB CHECK). 이 클래스에는 수량을 바꾸는 메서드가 없다.
    // 수량은 StockService 의 입고·차감 SQL 로만 바뀌고, 그때마다 원장(stock_movement)에 한 줄이 남는다.
    @Column(nullable = false)
    private Integer quantity;

    /**
     * 동시 수정 감지용. 차감 SQL에서도 같이 증가시킨다.
     * JPA 로 수정할 때 읽어 온 version 과 DB 의 version 이 다르면 저장을 실패시켜, 남의 변경을 덮어쓰지 않게 한다.
     */
    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // JPA 가 DB 에서 읽어 올 때 쓰는 기본 생성자 (Part 와 같은 이유)
    protected StockBalance() {
    }

    // 밖에서는 zeroFor() 로만 만든다 (처음 수량은 항상 0)
    private StockBalance(Part part, Integer quantity) {
        this.part = part;
        this.quantity = quantity;
        this.updatedAt = Instant.now();
    }

    /** 부품 등록과 같은 트랜잭션에서 수량 0인 잔액 행을 만든다. */
    public static StockBalance zeroFor(Part part) {
        return new StockBalance(part, 0);
    }

    // JPA 로 수정할 때만 동작한다. StockService 의 SQL 은 JPA 를 거치지 않아서 updated_at = now() 를 직접 쓴다.
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
