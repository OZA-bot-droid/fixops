CREATE TABLE part_request (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT       NOT NULL,
    part_id         BIGINT       REFERENCES part (id),
    part_name       VARCHAR(200) NOT NULL,
    sku             VARCHAR(64),
    category        VARCHAR(64),
    unit            VARCHAR(16),
    safety_stock    INTEGER      CHECK (safety_stock >= 0),
    quantity        INTEGER      NOT NULL CHECK (quantity > 0),
    work_order_id   BIGINT,
    reason          VARCHAR(500),
    status          VARCHAR(16)  NOT NULL DEFAULT 'REQUESTED' CHECK (status IN ('REQUESTED', 'APPROVED', 'REJECTED')),
    reject_reason   VARCHAR(500),
    requested_by    BIGINT       NOT NULL,
    decided_by      BIGINT,
    requested_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    decided_at      TIMESTAMPTZ,
    CONSTRAINT ck_part_request_reject_reason CHECK (status <> 'REJECTED' OR reject_reason IS NOT NULL)
);
CREATE INDEX idx_part_request_org_status ON part_request (organization_id, status, requested_at);
