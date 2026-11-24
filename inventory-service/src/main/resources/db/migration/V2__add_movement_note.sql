-- 입고 메모. 입고 요청의 note(최대 200자)를 원장에 함께 남긴다. 사용(USAGE) 등 다른 종류는 비워 둔다.
ALTER TABLE stock_movement ADD COLUMN note VARCHAR(200);
