package com.fixops.inventory.part.entity;

// 활성화, 비활성화 => 사용기록을 유지하기위해 삭제는 하지않기위해 수량이 없을시에는 비활성화
// (D6 메모) 수량에 따른 자동 비활성화는 팀장님과 결정 전이다. 지금은 상태를 바꾸는 코드가 없고, 등록 시 ACTIVE 만 쓴다.
//            비활성 부품은 입고할 수 없다 (D7, PART_INACTIVE 409).
public enum PartStatus {
    ACTIVE,
    INACTIVE
}
