-- Self-Synergy: "이 아이템이 이 챔피언과 얼마나 잘 맞는가"만 본다(구매 이력 무관).
-- 윈도 1 → recent = {16.17}
--
-- 야스오(157) MID — 전체 20판
--   16.17 10판: '6673,3031' ×7, '6673' ×1, '6672' ×2
--   16.15 10판: '3072' ×10
--   → all(분모 20):  3072 10 > 6673 8 > 3031 7 > 6672 2
--     recent(분모 10): 6673 8 > 3031 7 > 6672 2 > 3072 0
--   topK=1이면 all 기준으로는 3072만 남지만, recent union 덕에 6673이 살아남는다.
--
-- 888 — 포지션별 표본 불균형(rollup 백오프 검증)
--   TOP     2판  '3078'   ← 표본 부족
--   MIDDLE 20판  '3157'   ← rollup에는 두텁게 쌓임
DELETE FROM normalized_match_participants;
DELETE FROM champion_item_stats;
DELETE FROM champion_item_rollup;
DELETE FROM champion_pair_item_stats;
DELETE FROM item_meta_stats;

INSERT INTO normalized_match_participants (
    match_id, patch, queue_id, puuid, participant_id, champion_id, team_id, position, win,
    final_core_item_ids, core_item_purchase_order, core_item_purchase_order_complete
)
SELECT 'S17A-' || g, '16.17', 420, 's17a-' || g, 1, 157, 100, 'MIDDLE', g % 2 = 0,
       '6673,3031', '6673,3031', true
FROM generate_series(1, 7) g;

INSERT INTO normalized_match_participants (
    match_id, patch, queue_id, puuid, participant_id, champion_id, team_id, position, win,
    final_core_item_ids, core_item_purchase_order, core_item_purchase_order_complete
) VALUES
    ('S17B', '16.17', 420, 's17b', 1, 157, 100, 'MIDDLE', true, '6673', '6673', true),
    ('S17C', '16.17', 420, 's17c', 1, 157, 100, 'MIDDLE', true, '6672', '6672', true),
    ('S17D', '16.17', 420, 's17d', 1, 157, 100, 'MIDDLE', false, '6672', '6672', true);

INSERT INTO normalized_match_participants (
    match_id, patch, queue_id, puuid, participant_id, champion_id, team_id, position, win,
    final_core_item_ids, core_item_purchase_order, core_item_purchase_order_complete
)
SELECT 'S15-' || g, '16.15', 420, 's15-' || g, 1, 157, 100, 'MIDDLE', g % 2 = 0,
       '3072', '3072', true
FROM generate_series(1, 10) g;

-- 888: TOP 표본은 얇고 MIDDLE 표본은 두텁다
INSERT INTO normalized_match_participants (
    match_id, patch, queue_id, puuid, participant_id, champion_id, team_id, position, win,
    final_core_item_ids, core_item_purchase_order, core_item_purchase_order_complete
) VALUES
    ('T1', '16.17', 420, 't1', 1, 888, 100, 'TOP', true,  '3078', '3078', true),
    ('T2', '16.17', 420, 't2', 1, 888, 100, 'TOP', false, '3078', '3078', true);

INSERT INTO normalized_match_participants (
    match_id, patch, queue_id, puuid, participant_id, champion_id, team_id, position, win,
    final_core_item_ids, core_item_purchase_order, core_item_purchase_order_complete
)
SELECT 'M-' || g, '16.17', 420, 'm-' || g, 1, 888, 100, 'MIDDLE', g % 2 = 0,
       '3157', '3157', true
FROM generate_series(1, 20) g;
