-- inventory-service 초기 스키마. 자리표시자 V1__initialize_service.sql을 대체한다.
-- 주의: 적용된 Flyway SQL은 수정할 수 없다. D5 계약 동결 후 변경은 V2__... 로 추가한다.

CREATE TABLE part (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT       NOT NULL,
    sku             VARCHAR(64)  NOT NULL,
    name            VARCHAR(200) NOT NULL,
    category        VARCHAR(64),
    unit            VARCHAR(16)  NOT NULL DEFAULT 'EA',
    safety_stock    INTEGER      NOT NULL DEFAULT 0 CHECK (safety_stock >= 0),
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_part_org_sku UNIQUE (organization_id, sku)
);

-- 현재 재고(잔액). 부품 1개당 1행이며, 음수 재고는 DB가 최후 방어선으로 거부한다.
CREATE TABLE stock_balance (
    id         BIGSERIAL PRIMARY KEY,
    part_id    BIGINT      NOT NULL UNIQUE REFERENCES part (id),
    quantity   INTEGER     NOT NULL CHECK (quantity >= 0),
    version    BIGINT      NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 재고 원장(append-only). 정정은 수정/삭제가 아니라 보상 행(STOCK_RETURN 등) 추가로 한다.
-- 수정/삭제 금지는 서비스 코드 규칙으로 지킨다(원장에 UPDATE/DELETE 코드를 만들지 않는다).
CREATE TABLE stock_movement (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT       NOT NULL,
    part_id         BIGINT       NOT NULL REFERENCES part (id),
    movement_type   VARCHAR(32)  NOT NULL CHECK (movement_type IN ('RECEIPT', 'USAGE', 'STOCK_RETURN', 'ADJUSTMENT')),
    quantity_delta  INTEGER      NOT NULL CHECK (quantity_delta <> 0),
    work_order_id   BIGINT,
    operation_key   VARCHAR(100) NOT NULL,
    created_by      BIGINT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_movement_org_operation UNIQUE (organization_id, operation_key)
);
CREATE INDEX idx_stock_movement_part ON stock_movement (part_id, created_at);

-- 작업별 부품 사용 기록. work_order_id는 WorkOrder 서비스 소유이므로 FK를 걸지 않는다.
CREATE TABLE part_usage (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT       NOT NULL,
    work_order_id   BIGINT       NOT NULL,
    part_id         BIGINT       NOT NULL REFERENCES part (id),
    quantity        INTEGER      NOT NULL CHECK (quantity > 0),
    operation_key   VARCHAR(100) NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'CANCELLED')),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_usage_org_operation UNIQUE (organization_id, operation_key)
);
CREATE INDEX idx_part_usage_work_order ON part_usage (work_order_id);
