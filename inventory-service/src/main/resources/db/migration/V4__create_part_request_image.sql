CREATE TABLE part_request_image (
    id              BIGSERIAL PRIMARY KEY,
    request_id      BIGINT       NOT NULL REFERENCES part_request (id),
    organization_id BIGINT       NOT NULL,
    file_name       VARCHAR(200) NOT NULL,
    content_type    VARCHAR(32)  NOT NULL CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
    size_bytes      INTEGER      NOT NULL CHECK (size_bytes > 0 AND size_bytes <= 5242880),
    data            BYTEA        NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_part_request_image_request ON part_request_image (request_id);
