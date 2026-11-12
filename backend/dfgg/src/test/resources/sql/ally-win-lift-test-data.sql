-- 이중 차분이 아군 효과를 상쇄하는지 검증하는 픽스처.
--
-- 잔나(40) SUPPORT 100판, 50승 → 기준 승률 0.50
--   ├ 향로(3504)를 산 50판 중 25승          → 아이템 평소 승률 0.50
--   └ 징크스(222)와 함께한 40판 중 28승      → 조합 승률 0.70  ← 아군이 세다
--        └ 그중 향로를 산 20판 중 14승        → 0.70  ← 조합 평균과 같다 = 아이템은 특별하지 않다
--
-- 통제 안 함:  0.70 / 0.50 = 1.40   ← 아군이 세서 부풀려진다
-- 이중 차분:   1.40 / (0.70/0.50) = 1.00   ← 정확히 중립
DELETE FROM normalized_match_participants;

-- (A) 잔나+징크스, 향로 구매 20판 중 14승
INSERT INTO normalized_match_participants (
    match_id, patch, queue_id, puuid, participant_id, champion_id, team_id, position, tier, win,
    final_core_item_ids, core_item_purchase_order, core_item_purchase_order_complete
)
SELECT 'W_A' || g, '16.17', 420, 'wa-j-' || g, 1, 40, 100, 'UTILITY', 'EMERALD', g <= 14,
       '3504', '3504', true
FROM generate_series(1, 20) g
UNION ALL
SELECT 'W_A' || g, '16.17', 420, 'wa-x-' || g, 2, 222, 100, 'BOTTOM', 'EMERALD', g <= 14,
       '', '', false
FROM generate_series(1, 20) g;

-- (B) 잔나+징크스, 다른 아이템 20판 중 14승 → 조합 전체 40판 28승(0.70)
INSERT INTO normalized_match_participants (
    match_id, patch, queue_id, puuid, participant_id, champion_id, team_id, position, tier, win,
    final_core_item_ids, core_item_purchase_order, core_item_purchase_order_complete
)
SELECT 'W_B' || g, '16.17', 420, 'wb-j-' || g, 1, 40, 100, 'UTILITY', 'EMERALD', g <= 14,
       '6617', '6617', true
FROM generate_series(1, 20) g
UNION ALL
SELECT 'W_B' || g, '16.17', 420, 'wb-x-' || g, 2, 222, 100, 'BOTTOM', 'EMERALD', g <= 14,
       '', '', false
FROM generate_series(1, 20) g;

-- (C) 잔나 단독(징크스 없음), 향로 30판 중 11승 → 향로 전체 50판 25승(0.50)
INSERT INTO normalized_match_participants (
    match_id, patch, queue_id, puuid, participant_id, champion_id, team_id, position, tier, win,
    final_core_item_ids, core_item_purchase_order, core_item_purchase_order_complete
)
SELECT 'W_C' || g, '16.17', 420, 'wc-j-' || g, 1, 40, 100, 'UTILITY', 'EMERALD', g <= 11,
       '3504', '3504', true
FROM generate_series(1, 30) g;

-- (D) 잔나 단독, 다른 아이템 30판 중 11승 → 잔나 전체 100판 50승(0.50)
INSERT INTO normalized_match_participants (
    match_id, patch, queue_id, puuid, participant_id, champion_id, team_id, position, tier, win,
    final_core_item_ids, core_item_purchase_order, core_item_purchase_order_complete
)
SELECT 'W_D' || g, '16.17', 420, 'wd-j-' || g, 1, 40, 100, 'UTILITY', 'EMERALD', g <= 11,
       '6617', '6617', true
FROM generate_series(1, 30) g;
