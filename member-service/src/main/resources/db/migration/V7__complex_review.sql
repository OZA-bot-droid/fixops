ALTER TABLE complex_managements ADD COLUMN review_reason VARCHAR(500);
ALTER TABLE complex_managements ADD COLUMN reviewed_at TIMESTAMPTZ;
CREATE INDEX idx_complex_normalized_address ON apartment_complexes ((regexp_replace(lower(road_address), '[[:space:][:punct:]]', '', 'g')));
