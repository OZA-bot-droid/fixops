package com.fixops.inventory.part.exception;

import com.fixops.inventory.part.entity.PartStatus;

/**
 * 같은 조직에 같은 SKU가 이미 있을 때 (409).
 * 기존 부품의 id와 상태를 함께 들고 있어서, INACTIVE 이면 "재활성화하세요"라고 안내할 수 있다.
 * existingPartId 는 동시에 두 요청이 들어와 DB가 막은 경우처럼 알 수 없을 때 null 이다.
 */
public class DuplicateSkuException extends RuntimeException {

    private final Long existingPartId;
    private final PartStatus existingStatus;

    public DuplicateSkuException(String sku, Long existingPartId, PartStatus existingStatus) {
        super("이미 등록된 SKU입니다: " + sku);
        this.existingPartId = existingPartId;
        this.existingStatus = existingStatus;
    }

    public Long getExistingPartId() {
        return existingPartId;
    }

    public PartStatus getExistingStatus() {
        return existingStatus;
    }
}
