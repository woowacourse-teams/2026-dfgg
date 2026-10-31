ALTER TABLE champions
    ALTER COLUMN name TYPE jsonb
    USING jsonb_build_object('ko-KR', name);

ALTER TABLE champions
    ADD COLUMN url text;
