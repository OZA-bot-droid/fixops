package com.fixops.inventory.entity;

// 활성화, 비활성화 => 사용기록을 유지하기위해 삭제는 하지않기위해 수량이 없을시에는 비활성화
public enum PartStatus {
    ACTIVE,
    INACTIVE
}
