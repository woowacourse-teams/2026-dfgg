-- 기존 구매 테이블 하나를 전체 참가자 ITEM 이벤트 테이블로 확장한다.
ALTER TABLE normalized_match_participant_item_purchases
    RENAME TO normalized_match_participant_item_events;

ALTER TABLE normalized_match_participant_item_events
    ALTER COLUMN purchase_order DROP NOT NULL,
    ALTER COLUMN purchase_type DROP NOT NULL,
    ALTER COLUMN item_id DROP NOT NULL,
    ADD COLUMN event_type VARCHAR(32) NOT NULL DEFAULT 'ITEM_PURCHASED',
    ADD COLUMN event_order INTEGER CHECK (event_order > 0),
    ADD COLUMN frame_index INTEGER CHECK (frame_index >= 0),
    ADD COLUMN event_index INTEGER CHECK (event_index >= 0),
    ADD COLUMN undo_before_item_id INTEGER,
    ADD COLUMN undo_after_item_id INTEGER,
    ADD COLUMN normalization_version VARCHAR(32);

ALTER TABLE normalized_match_participant_item_events
    ALTER COLUMN event_type DROP DEFAULT,
    ADD CONSTRAINT uk_participant_item_event_order UNIQUE (match_id, participant_id, event_order),
    ADD CONSTRAINT uk_participant_item_event_source UNIQUE (match_id, participant_id, frame_index, event_index),
    ADD CONSTRAINT ck_participant_item_event_purchase CHECK (
        (event_type = 'ITEM_PURCHASED' AND purchase_order IS NOT NULL
            AND purchase_type IS NOT NULL AND item_id IS NOT NULL)
        OR (event_type <> 'ITEM_PURCHASED' AND purchase_order IS NULL
            AND purchase_type IS NULL AND item_cost IS NULL
            AND gold_lower IS NULL AND gold_upper IS NULL AND gold_range_status IS NULL)
    ),
    -- 기존 행은 원천 좌표가 없는 상태로 보존하며 재정규화 시 전체 이벤트로 교체한다.
    ADD CONSTRAINT ck_participant_item_event_source CHECK (
        (event_order IS NULL AND frame_index IS NULL AND event_index IS NULL AND normalization_version IS NULL)
        OR (event_order IS NOT NULL AND frame_index IS NOT NULL
            AND event_index IS NOT NULL AND normalization_version IS NOT NULL)
    );

CREATE INDEX idx_participant_item_event_match_order
    ON normalized_match_participant_item_events (match_id, participant_id, event_order);
