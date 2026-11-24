-- 카테고리를 산업별 공통 목록에서 조직별 목록으로 전환한다.
ALTER TABLE asset_category ADD COLUMN organization_id BIGINT;

-- 기존 공통 카테고리를 현재 설비가 속한 조직별로 복사한다.
CREATE TEMPORARY TABLE legacy_asset_category ON COMMIT DROP AS
SELECT id, industry_type, name FROM asset_category;

ALTER TABLE asset_category DROP CONSTRAINT uq_asset_category_industry_name;

INSERT INTO asset_category (organization_id, industry_type, name)
SELECT DISTINCT a.organization_id, legacy.industry_type, legacy.name
FROM (SELECT DISTINCT organization_id FROM asset) a
CROSS JOIN legacy_asset_category legacy;

-- 각 설비가 자기 조직으로 복사된 카테고리를 참조하도록 이전한다.
UPDATE asset a
SET category_id = owned.id
FROM legacy_asset_category legacy
JOIN asset_category owned
  ON owned.industry_type = legacy.industry_type
 AND owned.name = legacy.name
WHERE a.category_id = legacy.id
  AND owned.organization_id = a.organization_id;

-- 참조가 새 조직별 카테고리로 이전된 뒤 공통 카테고리를 제거한다.
DELETE FROM asset_category WHERE organization_id IS NULL;

ALTER TABLE asset_category ALTER COLUMN organization_id SET NOT NULL;
ALTER TABLE asset_category
    ADD CONSTRAINT uq_asset_category_org_industry_name
    UNIQUE (organization_id, industry_type, name);

