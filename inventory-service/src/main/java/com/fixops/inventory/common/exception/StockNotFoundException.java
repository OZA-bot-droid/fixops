package com.fixops.inventory.common.exception;

//부품의 재고 행이 없을 때 (RESOURCE_NOT_FOUND, 404)
public class StockNotFoundException extends RuntimeException {
    public StockNotFoundException(String message) {
        super(message);
    }
}
