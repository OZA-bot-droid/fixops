-- PUBLIC_HOLIDAY 테이블
-- 공휴일 데이터
CREATE TABLE public_holiday
(
    -- 	실제 공휴일 날짜
    holiday_date DATE PRIMARY KEY,

    -- 	공휴일 명칭
    holiday_name TEXT        NOT NULL,

    -- 해당 데이터 수집 기준 시각
    synced_at    TIMESTAMPTZ NOT NULL,

    CONSTRAINT chk_public_holiday_name
        CHECK (LENGTH(BTRIM(holiday_name)) > 0)

);


-- HOLIDAY_SYNC_MONTH 테이블
-- 월별 공휴일 데이터 동기화 상태
CREATE TABLE holiday_sync_month
(
    -- 수집 대상 월의 1일
    target_month    DATE PRIMARY KEY,

    -- 	마지막 정상 수집 기준 시각
    last_success_at TIMESTAMPTZ NOT NULL,

    -- 	해당 월의 공휴일 날짜 개수
    holiday_count   INTEGER     NOT NULL,

    CONSTRAINT chk_holiday_count
        CHECK (holiday_count >= 0),

    CONSTRAINT chk_holiday_target_month
        CHECK (EXTRACT(DAY FROM target_month) = 1)

);