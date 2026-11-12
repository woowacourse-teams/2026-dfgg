-- 야스오 MID 1,000판. 모든 판에 람머스가 적으로 있어 pair 표본은 충분하다.
-- 구매율:  무한의 대검(3031) 300/1000 = 30%
--          불멸의 철갑궁(6673) 698/1000 = 69.8%
--          라바돈(3089) 2/1000 = 0.2%   ← 람머스전에서만 나온 우연
--
-- 하한 0%   → 셋 다 후보
-- 하한 1%   → 라바돈 탈락 (0.2% < 1%)
-- 하한 50%  → 무한의 대검도 탈락 (30% < 50%), 철갑궁만 남는다
DELETE FROM normalized_match_participants;

INSERT INTO normalized_match_participants (
    match_id, patch, queue_id, puuid, participant_id, champion_id, team_id, position, tier, win,
    final_core_item_ids, core_item_purchase_order, core_item_purchase_order_complete
)
SELECT 'C_A' || g, '16.17', 420, 'ca-' || g, 1, 157, 100, 'MIDDLE', 'EMERALD', true,
       '3031', '3031', true
FROM generate_series(1, 300) g;

INSERT INTO normalized_match_participants (
    match_id, patch, queue_id, puuid, participant_id, champion_id, team_id, position, tier, win,
    final_core_item_ids, core_item_purchase_order, core_item_purchase_order_complete
)
SELECT 'C_B' || g, '16.17', 420, 'cb-' || g, 1, 157, 100, 'MIDDLE', 'EMERALD', true,
       '6673', '6673', true
FROM generate_series(1, 698) g;

INSERT INTO normalized_match_participants (
    match_id, patch, queue_id, puuid, participant_id, champion_id, team_id, position, tier, win,
    final_core_item_ids, core_item_purchase_order, core_item_purchase_order_complete
) VALUES
    ('C_R1', '16.17', 420, 'cr1-y', 1, 157, 100, 'MIDDLE', 'EMERALD', true,  '3089', '3089', true),
    ('C_R2', '16.17', 420, 'cr2-y', 1, 157, 100, 'MIDDLE', 'EMERALD', false, '3089', '3089', true);

-- 적 람머스. 구매 기록은 없어도 pair의 맥락으로 남는다.
INSERT INTO normalized_match_participants (
    match_id, patch, queue_id, puuid, participant_id, champion_id, team_id, position, tier, win,
    final_core_item_ids, core_item_purchase_order, core_item_purchase_order_complete
)
SELECT 'C_A' || g, '16.17', 420, 'ca-r-' || g, 6, 33, 200, 'TOP', 'EMERALD', false, '', '', false
FROM generate_series(1, 300) g;

INSERT INTO normalized_match_participants (
    match_id, patch, queue_id, puuid, participant_id, champion_id, team_id, position, tier, win,
    final_core_item_ids, core_item_purchase_order, core_item_purchase_order_complete
)
SELECT 'C_B' || g, '16.17', 420, 'cb-r-' || g, 6, 33, 200, 'TOP', 'EMERALD', false, '', '', false
FROM generate_series(1, 698) g;

INSERT INTO normalized_match_participants (
    match_id, patch, queue_id, puuid, participant_id, champion_id, team_id, position, tier, win,
    final_core_item_ids, core_item_purchase_order, core_item_purchase_order_complete
) VALUES
    ('C_R1', '16.17', 420, 'cr1-r', 6, 33, 200, 'TOP', 'EMERALD', false, '', '', false),
    ('C_R2', '16.17', 420, 'cr2-r', 6, 33, 200, 'TOP', 'EMERALD', true,  '', '', false);
