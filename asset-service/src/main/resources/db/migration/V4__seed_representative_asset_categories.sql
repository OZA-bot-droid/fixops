-- 이미 Location, Asset 또는 AssetCategory 데이터에서 확인되는 조직에
-- 아파트·공장 설비 등록용 시작 카테고리를 추가한다.
-- 기존 카테고리는 이력과 참조 보존을 위해 삭제하거나 변경하지 않는다.
WITH known_organizations AS (
    SELECT organization_id FROM location
    UNION
    SELECT organization_id FROM asset
    UNION
    SELECT organization_id FROM asset_category
),
default_categories(industry_type, name) AS (
    VALUES
        ('BUILDING', '승강기'),
        ('BUILDING', '보일러·열교환기'),
        ('BUILDING', '급수펌프'),
        ('BUILDING', '배수·집수정펌프'),
        ('BUILDING', '급탕순환펌프'),
        ('BUILDING', '저수조'),
        ('BUILDING', '공조기·환기팬'),
        ('BUILDING', '비상발전기'),
        ('BUILDING', '변압기·수배전반'),
        ('BUILDING', '소화펌프'),
        ('BUILDING', '화재수신반'),
        ('BUILDING', 'CCTV'),
        ('BUILDING', '주차차단기'),
        ('BUILDING', '어린이놀이시설'),
        ('BUILDING', '공용조명'),
        ('FACTORY', '머시닝센터(MCT)'),
        ('FACTORY', 'CNC 선반'),
        ('FACTORY', '범용 선반'),
        ('FACTORY', '밀링기'),
        ('FACTORY', '연삭기'),
        ('FACTORY', '방전기'),
        ('FACTORY', '프레스'),
        ('FACTORY', '사출성형기'),
        ('FACTORY', '레이저 절단기'),
        ('FACTORY', '절곡기'),
        ('FACTORY', '용접기'),
        ('FACTORY', '용접로봇'),
        ('FACTORY', '자동 조립기'),
        ('FACTORY', '체결기'),
        ('FACTORY', '충진기'),
        ('FACTORY', '포장기'),
        ('FACTORY', '컨베이어'),
        ('FACTORY', '호이스트'),
        ('FACTORY', '비전검사기'),
        ('FACTORY', '삼차원측정기(CMM)'),
        ('FACTORY', '에어컴프레서'),
        ('FACTORY', '칠러'),
        ('FACTORY', '집진기'),
        ('FACTORY', '보일러'),
        ('FACTORY', '공조·환기설비'),
        ('FACTORY', '급수·배수펌프'),
        ('FACTORY', '전기·제어반')
)
INSERT INTO asset_category (organization_id, industry_type, name)
SELECT organizations.organization_id, defaults.industry_type, defaults.name
FROM known_organizations organizations
CROSS JOIN default_categories defaults
ON CONFLICT (organization_id, industry_type, name) DO NOTHING;
