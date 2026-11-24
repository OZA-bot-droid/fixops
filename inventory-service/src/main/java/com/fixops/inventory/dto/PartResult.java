package com.fixops.inventory.dto;

import com.fixops.inventory.entity.Part;
import com.fixops.inventory.entity.PartStatus;
import com.fixops.inventory.entity.StockBalance;
import com.fixops.inventory.repository.PartStockRow;
import java.time.Instant;

/**
 * 부품 한 건의 결과 (Service 출구). OpenAPI 의 Part 스키마와 같은 필드.
 * 부품 정보(part)와 현재 수량(stock_balance)을 합친 모양이다.
 */
public record PartResult(
        long id,
        String sku,
        String name,
        String category,
        String unit,
        int safetyStock,
        PartStatus status,
        int quantity,
        Instant createdAt,
        Instant updatedAt) {

    /** 재고 행에서 부품까지 꺼내 결과로 바꾼다. */
    public static PartResult from(StockBalance balance) {
        Part p = balance.getPart();
        return new PartResult(
                p.getId(), p.getSku(), p.getName(), p.getCategory(), p.getUnit(),
                p.getSafetyStock(), p.getStatus(), balance.getQuantity(),
                p.getCreatedAt(), p.getUpdatedAt());
    }

    /** 목록 조회(SQL)의 결과 한 줄을 결과로 바꾼다. status 는 DB 글자('ACTIVE')를 enum 으로 바꾼다. */
    public static PartResult from(PartStockRow row) {
        return new PartResult(
                row.getId(), row.getSku(), row.getName(), row.getCategory(), row.getUnit(),
                row.getSafetyStock(), PartStatus.valueOf(row.getStatus()), row.getQuantity(),
                row.getCreatedAt(), row.getUpdatedAt());
    }
}
