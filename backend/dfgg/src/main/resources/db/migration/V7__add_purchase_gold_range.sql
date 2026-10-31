-- 기존 구매 행은 범위를 계산하지 않은 상태로 남겨 재정규화 전까지 관측값으로 취급하지 않는다.
-- 구매 순간의 정확한 currentGold는 Raw Timeline에 없으므로 구매 테이블에서 제거한다.
ALTER TABLE normalized_match_participant_item_purchases
    DROP COLUMN current_gold,
    ADD COLUMN gold_lower INTEGER,
    ADD COLUMN gold_upper INTEGER,
    ADD COLUMN gold_range_status VARCHAR(32);

-- 범위를 계산할 수 없으면 세 값 모두 NULL이다. 계산할 수 있으면 양끝과 근거를 함께 저장한다.
ALTER TABLE normalized_match_participant_item_purchases
    ADD CONSTRAINT ck_participant_purchase_gold_range CHECK (
        (gold_lower IS NULL AND gold_upper IS NULL AND gold_range_status IS NULL)
        OR (gold_lower IS NOT NULL AND gold_upper IS NOT NULL AND gold_range_status IS NOT NULL
            AND gold_range_status IN ('BASE', 'TOTAL_GOLD_REFINED')
            AND gold_lower >= 0 AND gold_upper >= gold_lower)
    );
