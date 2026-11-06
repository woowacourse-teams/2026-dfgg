ALTER TABLE items
    ALTER COLUMN name TYPE jsonb
    USING jsonb_build_object('ko-KR', name);

ALTER TABLE items
    ADD COLUMN gold jsonb;

ALTER TABLE items
    ADD COLUMN url text;

ALTER TABLE items
    ADD COLUMN from_item_ids jsonb,
    ADD COLUMN into_item_ids jsonb;
