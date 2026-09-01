-- 스펙 §3의 결함 시나리오를 그대로 데이터로 만든다.
--
--   우리 팀: 야스오(157) + 아리(103) + 징크스(222)
--   적:      람머스(33)
--   아리는 람머스를 만나면 리안드리(6653)를 산다.  야스오는 절대 안 산다.
--
-- 기존 구조는 [적 챔피언 + 우리 팀이 산 아이템]이라 (람머스, 리안드리) 연관이 강해지고,
-- 그 연관이 야스오의 추천에까지 흘러들었다. 새 구조는 구매자가 누구인지를 키에 담는다.
--
-- 야스오가 람머스 상대로 실제로 더 사는 것: 도미닉 경의 인사(3036) — 20판 중 16판.
-- 야스오의 평소 3036 구매율은 40판 중 18판(45%)이므로 lift가 1보다 커야 한다.
-- 야스오의 리안드리 구매율은 0/40이라 base rate가 바닥이다.
DELETE FROM normalized_match_participants;
DELETE FROM champion_item_stats;
DELETE FROM champion_item_rollup;
DELETE FROM champion_pair_item_stats;
DELETE FROM item_meta_stats;

-- 람머스를 만난 20판: 야스오는 도미닉 16판 / 무한의 대검 4판, 아리는 리안드리 20판
INSERT INTO normalized_match_participants (
    match_id, patch, queue_id, puuid, participant_id, champion_id, team_id, position, win,
    final_core_item_ids, core_item_purchase_order, core_item_purchase_order_complete
)
SELECT 'CR-' || g, '16.17', 420, 'cr-y-' || g, 1, 157, 100, 'MIDDLE', g % 2 = 0,
       '3036', '3036', true
FROM generate_series(1, 16) g
UNION ALL
SELECT 'CR-' || g, '16.17', 420, 'cr-y-' || g, 1, 157, 100, 'MIDDLE', g % 2 = 0,
       '3031', '3031', true
FROM generate_series(17, 20) g
UNION ALL
SELECT 'CR-' || g, '16.17', 420, 'cr-a-' || g, 2, 103, 100, 'BOTTOM', g % 2 = 0,
       '6653', '6653', true
FROM generate_series(1, 20) g
UNION ALL
SELECT 'CR-' || g, '16.17', 420, 'cr-e-' || g, 6, 33, 200, 'TOP', g % 2 = 1,
       '3068', '3068', true
FROM generate_series(1, 20) g;

-- 람머스가 아닌 적(아리 103을 적으로)을 만난 20판: 야스오는 도미닉 2판 / 무한의 대검 18판
INSERT INTO normalized_match_participants (
    match_id, patch, queue_id, puuid, participant_id, champion_id, team_id, position, win,
    final_core_item_ids, core_item_purchase_order, core_item_purchase_order_complete
)
SELECT 'CO-' || g, '16.17', 420, 'co-y-' || g, 1, 157, 100, 'MIDDLE', g % 2 = 0,
       '3036', '3036', true
FROM generate_series(1, 2) g
UNION ALL
SELECT 'CO-' || g, '16.17', 420, 'co-y-' || g, 1, 157, 100, 'MIDDLE', g % 2 = 0,
       '3031', '3031', true
FROM generate_series(3, 20) g
UNION ALL
SELECT 'CO-' || g, '16.17', 420, 'co-e-' || g, 6, 103, 200, 'MIDDLE', g % 2 = 1,
       '6653', '6653', true
FROM generate_series(1, 20) g;
