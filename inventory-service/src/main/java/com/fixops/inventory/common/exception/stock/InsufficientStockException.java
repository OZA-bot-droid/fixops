package com.fixops.inventory.common.exception.stock;

// 재고부족 (INSUFFICIENT_STOCK, 409)
public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String message) {
        super(message);
    }
}
