package com.fixops.inventory.part.dto;

import com.fixops.inventory.part.entity.PartStatus;
import java.util.List;

/**
 * 목록 조회의 상태 필터 (OpenAPI status 파라미터: ACTIVE, INACTIVE, ALL).
 * PartStatus 에는 ALL 이 없으므로 조회 전용으로 따로 둔다.
 */
public enum PartStatusFilter {
    ACTIVE, INACTIVE, ALL;

    /** 쿼리의 "p.status in :statuses" 에 넣을 목록으로 바꾼다. */
    public List<PartStatus> toStatuses() {
        return switch (this) {
            case ACTIVE -> List.of(PartStatus.ACTIVE);
            case INACTIVE -> List.of(PartStatus.INACTIVE);
            case ALL -> List.of(PartStatus.ACTIVE, PartStatus.INACTIVE);
        };
    }

    /** SQL 의 "p.status IN (:statuses)" 에 넣을 글자 목록 ('ACTIVE', 'INACTIVE'). */
    public List<String> toStatusNames() {
        return toStatuses().stream().map(Enum::name).toList();
    }
}
