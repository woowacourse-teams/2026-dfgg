-- 잔나 SUPPORT 1,000판. 모든 판에 징크스가 아군으로 있어 pair 표본은 충분하다.
-- 구매율:  월석 재생기(6617) 300/1000 = 30%
--          불타는 향로(3504) 698/1000 = 69.8%
--          라바돈(3089) 2/1000 = 0.2%   ← 징크스와 함께일 때만 나온 우연
--
-- 하한 0%   → 셋 다 후보
-- 하한 1%   → 라바돈 탈락 (0.2% < 1%)
-- 하한 50%  → 월석도 탈락 (30% < 50%), 향로만 남는다
DELETE FROM normalized_match_participants;

INSERT INTO normalized_match_participants (
    match_id, patch, queue_id, puuid, participant_id, champion_id, team_id, position, tier, win,
    final_core_item_ids, core_item_purchase_order, core_item_purchase_order_complete
)
SELECT 'AF_A' || g, '16.17', 420, 'afa-' || g, 1, 40, 100, 'UTILITY', 'EMERALD', true,
       '6617', '6617', true
FROM generate_series(1, 300) g;

INSERT INTO normalized_match_participants (
    match_id, patch, queue_id, puuid, participant_id, champion_id, team_id, position, tier, win,
    final_core_item_ids, core_item_purchase_order, core_item_purchase_order_complete
)
SELECT 'AF_B' || g, '16.17', 420, 'afb-' || g, 1, 40, 100, 'UTILITY', 'EMERALD', true,
       '3504', '3504', true
FROM generate_series(1, 698) g;

INSERT INTO normalized_match_participants (
    match_id, patch, queue_id, puuid, participant_id, champion_id, team_id, position, tier, win,
    final_core_item_ids, core_item_purchase_order, core_item_purchase_order_complete
) VALUES
    ('AF_R1', '16.17', 420, 'afr1-j', 1, 40, 100, 'UTILITY', 'EMERALD', true,  '3089', '3089', true),
    ('AF_R2', '16.17', 420, 'afr2-j', 1, 40, 100, 'UTILITY', 'EMERALD', false, '3089', '3089', true);

-- 아군 징크스. 구매 기록은 없어도 pair의 맥락으로 남는다.
INSERT INTO normalized_match_participants (
    match_id, patch, queue_id, puuid, participant_id, champion_id, team_id, position, tier, win,
    final_core_item_ids, core_item_purchase_order, core_item_purchase_order_complete
)
SELECT 'AF_A' || g, '16.17', 420, 'afa-x-' || g, 2, 222, 100, 'BOTTOM', 'EMERALD', true, '', '', false
FROM generate_series(1, 300) g;

INSERT INTO normalized_match_participants (
    match_id, patch, queue_id, puuid, participant_id, champion_id, team_id, position, tier, win,
    final_core_item_ids, core_item_purchase_order, core_item_purchase_order_complete
)
SELECT 'AF_B' || g, '16.17', 420, 'afb-x-' || g, 2, 222, 100, 'BOTTOM', 'EMERALD', true, '', '', false
FROM generate_series(1, 698) g;

INSERT INTO normalized_match_participants (
    match_id, patch, queue_id, puuid, participant_id, champion_id, team_id, position, tier, win,
    final_core_item_ids, core_item_purchase_order, core_item_purchase_order_complete
) VALUES
    ('AF_R1', '16.17', 420, 'afr1-x', 2, 222, 100, 'BOTTOM', 'EMERALD', true,  '', '', false),
    ('AF_R2', '16.17', 420, 'afr2-x', 2, 222, 100, 'BOTTOM', 'EMERALD', false, '', '', false);
