-- 잔나(40)는 SUPPORT만 200판 했다. MID 통계는 한 판도 없다 — off-role 요청을 재현한다.
--
--   OR_A 50판: 징크스(222) 아군, 람머스(33) 적, 잔나가 불타는 향로(3504)
--   OR_B 50판: 징크스(222) 아군, 람머스(33) 적, 잔나가 슈렐리아의 군가(2065)
--   OR_C 100판: 코그모(96) 아군,                 잔나가 슈렐리아의 군가(2065)
--
-- 챔피언 전체(rollup): 향로 50/200 = 25%, 슈렐리아 150/200 = 75%
-- 징크스와 함께(=람머스 상대로도): 향로 50/100 = 50%, 슈렐리아 50/100 = 50%
--
-- 올바른 lift: 향로 ≈ 2.0, 슈렐리아 ≈ 0.67 — 향로가 앞선다.
-- 분모가 0이면 둘 다 1.0으로 동점이 되고 ID가 작은 슈렐리아(2065)가 앞선다.
DELETE FROM normalized_match_participants;

INSERT INTO normalized_match_participants (
    match_id, patch, queue_id, puuid, participant_id, champion_id, team_id, position, tier, win,
    final_core_item_ids, core_item_purchase_order, core_item_purchase_order_complete
)
SELECT 'OR_A' || g, '16.17', 420, 'ora-janna-' || g, 1, 40, 100, 'UTILITY', 'EMERALD', true,
       '3504', '3504', true
FROM generate_series(1, 50) g;

INSERT INTO normalized_match_participants (
    match_id, patch, queue_id, puuid, participant_id, champion_id, team_id, position, tier, win,
    final_core_item_ids, core_item_purchase_order, core_item_purchase_order_complete
)
SELECT 'OR_B' || g, '16.17', 420, 'orb-janna-' || g, 1, 40, 100, 'UTILITY', 'EMERALD', true,
       '2065', '2065', true
FROM generate_series(1, 50) g;

INSERT INTO normalized_match_participants (
    match_id, patch, queue_id, puuid, participant_id, champion_id, team_id, position, tier, win,
    final_core_item_ids, core_item_purchase_order, core_item_purchase_order_complete
)
SELECT 'OR_C' || g, '16.17', 420, 'orc-janna-' || g, 1, 40, 100, 'UTILITY', 'EMERALD', true,
       '2065', '2065', true
FROM generate_series(1, 100) g;

-- 아군·적은 구매 기록 없이 pair의 맥락으로만 남는다.
INSERT INTO normalized_match_participants (
    match_id, patch, queue_id, puuid, participant_id, champion_id, team_id, position, tier, win,
    final_core_item_ids, core_item_purchase_order, core_item_purchase_order_complete
)
SELECT prefix || g, '16.17', 420, prefix || '-jinx-' || g, 2, 222, 100, 'BOTTOM', 'EMERALD', true, '', '', false
FROM generate_series(1, 50) g, (VALUES ('OR_A'), ('OR_B')) AS matches(prefix);

INSERT INTO normalized_match_participants (
    match_id, patch, queue_id, puuid, participant_id, champion_id, team_id, position, tier, win,
    final_core_item_ids, core_item_purchase_order, core_item_purchase_order_complete
)
SELECT prefix || g, '16.17', 420, prefix || '-rammus-' || g, 6, 33, 200, 'TOP', 'EMERALD', false, '', '', false
FROM generate_series(1, 50) g, (VALUES ('OR_A'), ('OR_B')) AS matches(prefix);

INSERT INTO normalized_match_participants (
    match_id, patch, queue_id, puuid, participant_id, champion_id, team_id, position, tier, win,
    final_core_item_ids, core_item_purchase_order, core_item_purchase_order_complete
)
SELECT 'OR_C' || g, '16.17', 420, 'orc-kogmaw-' || g, 2, 96, 100, 'BOTTOM', 'EMERALD', true, '', '', false
FROM generate_series(1, 100) g;
