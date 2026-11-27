-- 부품 사진 (관리자가 부품 등록할 때 1장, DB 저장). 부품 하나에 사진 하나라 part_id 가 기본 키
CREATE TABLE part_image (
    part_id         BIGINT       PRIMARY KEY REFERENCES part (id),
    organization_id BIGINT       NOT NULL,
    file_name       VARCHAR(200) NOT NULL,
    content_type    VARCHAR(32)  NOT NULL CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
    size_bytes      INTEGER      NOT NULL CHECK (size_bytes > 0 AND size_bytes <= 5242880),
    data            BYTEA        NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
