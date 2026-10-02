-- 기존 구매 행은 유지하고, 이후 정규화하는 구매에 참가자 포지션을 기록한다.
ALTER TABLE normalized_match_participant_item_purchases
    ADD COLUMN position VARCHAR(32);
