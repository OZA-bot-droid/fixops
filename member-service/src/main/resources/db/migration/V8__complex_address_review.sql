ALTER TABLE complex_managements ADD COLUMN address_collision_confirmed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE complex_managements ADD CONSTRAINT ck_address_collision_review CHECK (NOT address_collision_confirmed OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL AND review_reason IS NOT NULL));
