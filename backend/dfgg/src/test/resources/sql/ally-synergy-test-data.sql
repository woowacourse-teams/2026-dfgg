-- Ally-Synergy: [내 챔피언 + 아군 챔피언 하나 + 아이템] 삼중항.
-- 스펙의 예시를 그대로 재현한다 — 같은 잔나라도 아군 ADC가 누구냐에 따라 사는 게 달라진다.
--
--   잔나(40) + 징크스(222) 10판 → 불타는 향로(3504) 8판, 미카엘(3222) 2판
--   잔나(40) + 코그모(96)  10판 → 월석 재생기(6617) 9판, 향로(3504) 1판
--   잔나(40) + 오른(516)    1판 → 솔라리(3190) 1판   ← 지지도 부족(τ=5 미만)
--
-- 아군 window를 5명 통짜로 만들면 이 구분이 사라진다.
DELETE FROM normalized_match_participants;
DELETE FROM champion_item_stats;
DELETE FROM champion_item_rollup;
DELETE FROM champion_pair_item_stats;
DELETE FROM item_meta_stats;

-- 잔나 + 징크스: 향로 8판
INSERT INTO normalized_match_participants (
    match_id, patch, queue_id, puuid, participant_id, champion_id, team_id, position, win,
    final_core_item_ids, core_item_purchase_order, core_item_purchase_order_complete
)
SELECT 'AJ-' || g, '16.17', 420, 'aj-j-' || g, 1, 40, 100, 'UTILITY', g % 2 = 0, '3504', '3504', true
FROM generate_series(1, 8) g
UNION ALL
SELECT 'AJ-' || g, '16.17', 420, 'aj-a-' || g, 2, 222, 100, 'BOTTOM', g % 2 = 0, '3031', '3031', true
FROM generate_series(1, 8) g
UNION ALL
SELECT 'AJ-' || g, '16.17', 420, 'aj-e-' || g, 6, 33, 200, 'TOP', g % 2 = 1, '3068', '3068', true
FROM generate_series(1, 8) g;

-- 잔나 + 징크스: 미카엘 2판
INSERT INTO normalized_match_participants (
    match_id, patch, queue_id, puuid, participant_id, champion_id, team_id, position, win,
    final_core_item_ids, core_item_purchase_order, core_item_purchase_order_complete
)
SELECT 'AJ2-' || g, '16.17', 420, 'aj2-j-' || g, 1, 40, 100, 'UTILITY', true, '3222', '3222', true
FROM generate_series(1, 2) g
UNION ALL
SELECT 'AJ2-' || g, '16.17', 420, 'aj2-a-' || g, 2, 222, 100, 'BOTTOM', true, '3031', '3031', true
FROM generate_series(1, 2) g
UNION ALL
SELECT 'AJ2-' || g, '16.17', 420, 'aj2-e-' || g, 6, 33, 200, 'TOP', false, '3068', '3068', true
FROM generate_series(1, 2) g;

-- 잔나 + 코그모: 월석 9판
INSERT INTO normalized_match_participants (
    match_id, patch, queue_id, puuid, participant_id, champion_id, team_id, position, win,
    final_core_item_ids, core_item_purchase_order, core_item_purchase_order_complete
)
SELECT 'AK-' || g, '16.17', 420, 'ak-j-' || g, 1, 40, 100, 'UTILITY', g % 2 = 0, '6617', '6617', true
FROM generate_series(1, 9) g
UNION ALL
SELECT 'AK-' || g, '16.17', 420, 'ak-a-' || g, 2, 96, 100, 'BOTTOM', g % 2 = 0, '3031', '3031', true
FROM generate_series(1, 9) g
UNION ALL
SELECT 'AK-' || g, '16.17', 420, 'ak-e-' || g, 6, 33, 200, 'TOP', g % 2 = 1, '3068', '3068', true
FROM generate_series(1, 9) g;

-- 잔나 + 코그모: 향로 1판
INSERT INTO normalized_match_participants (
    match_id, patch, queue_id, puuid, participant_id, champion_id, team_id, position, win,
    final_core_item_ids, core_item_purchase_order, core_item_purchase_order_complete
) VALUES
    ('AK2', '16.17', 420, 'ak2-j', 1, 40, 100, 'UTILITY', true, '3504', '3504', true),
    ('AK2', '16.17', 420, 'ak2-a', 2, 96, 100, 'BOTTOM', true, '3031', '3031', true),
    ('AK2', '16.17', 420, 'ak2-e', 6, 33, 200, 'TOP', false, '3068', '3068', true),
-- 잔나 + 오른: 단 1판 (지지도 부족)
    ('AO', '16.17', 420, 'ao-j', 1, 40, 100, 'UTILITY', true, '3190', '3190', true),
    ('AO', '16.17', 420, 'ao-a', 2, 516, 100, 'TOP', true, '3068', '3068', true),
    ('AO', '16.17', 420, 'ao-e', 6, 33, 200, 'MIDDLE', false, '3068', '3068', true);
