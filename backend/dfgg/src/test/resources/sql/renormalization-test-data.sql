-- 재정규화 대상 선정 검증용.
--
--   R1  raw + timeline + 정규화(PLATINUM)  → 대상 ✓
--   R2  raw + timeline + 정규화(PLATINUM)  → 대상 ✓
--   R3  raw + timeline + 정규화(EMERALD)   → PLATINUM 요청 시 제외
--   R4  raw만 (timeline 없음) + 정규화      → 원본이 불완전해 재정규화 불가, 제외
--   R5  raw + timeline, 정규화 없음         → 기존 pending 경로의 몫이지 재정규화 대상 아님
DELETE FROM normalized_match_participants;
DELETE FROM raw_match_timelines;
DELETE FROM raw_matches;

INSERT INTO raw_matches (match_id, raw_data) VALUES
    ('R1', '{}'), ('R2', '{}'), ('R3', '{}'), ('R4', '{}'), ('R5', '{}');

INSERT INTO raw_match_timelines (match_id, raw_data) VALUES
    ('R1', '{}'), ('R2', '{}'), ('R3', '{}'), ('R5', '{}');

INSERT INTO normalized_match_participants (
    match_id, patch, queue_id, puuid, participant_id, champion_id, team_id, position, tier, win,
    final_core_item_ids, core_item_purchase_order, core_item_purchase_order_complete
) VALUES
    ('R1', '16.17', 420, 'r1-a', 1, 157, 100, 'MIDDLE', 'PLATINUM', true, '3031', '3031', true),
    ('R2', '16.17', 420, 'r2-a', 1, 157, 100, 'MIDDLE', 'PLATINUM', true, '3031', '3031', true),
    ('R3', '16.17', 420, 'r3-a', 1, 157, 100, 'MIDDLE', 'EMERALD',  true, '3031', '3031', true),
    ('R4', '16.17', 420, 'r4-a', 1, 157, 100, 'MIDDLE', 'PLATINUM', true, '3031', '3031', true);
