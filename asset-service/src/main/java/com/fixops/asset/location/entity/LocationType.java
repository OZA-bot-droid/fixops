package com.fixops.asset.location.entity;

public enum LocationType {
    COMPLEX,
    BUILDING,
    UNIT,
    COMMON_AREA,
    FACTORY,
    AREA,
    LINE,
    EQUIPMENT_LOCATION;

    /** v1.0에 정의된 아파트/공장 위치 계층만 허용한다. */
    public boolean canContain(LocationType child) {
        return switch (this) {
            case COMPLEX -> child == BUILDING || child == COMMON_AREA;
            case BUILDING -> child == UNIT || child == COMMON_AREA;
            case FACTORY -> child == AREA;
            case AREA -> child == LINE;
            case LINE -> child == EQUIPMENT_LOCATION;
            case UNIT, COMMON_AREA, EQUIPMENT_LOCATION -> false;
        };
    }

    /** 루트 노드는 아파트 단지 또는 공장이다. */
    public boolean isRoot() {
        return this == COMPLEX || this == FACTORY;
    }
}
