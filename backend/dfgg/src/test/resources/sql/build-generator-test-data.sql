-- 야스오(157) MID 구매 순서 표본. 윈도 1 → recent = {16.17}
--
-- 전개 표본(정확 prefix / bigram 검증용)
--   6673,3031,3036 ×3 (16.17)   [6673,3031] 다음 → 3036
--   6673,3031,3006 ×2 (16.17)   [6673,3031] 다음 → 3006
--   6673,3072      ×4 (16.15)   6673 다음 → 3072
--   3031,6673      ×1 (16.15)
--
-- recent/all union 검증용 — 이 셋의 대비가 핵심이다
--   3072 ×12 (16.15)  전체 16회 / 최근 0회  → all 기준 1위, recent 기준 꼴찌
--   3033 ×11 (16.15)  전체 11회 / 최근 0회  → all 기준 2위
--   3153 ×10 (16.17)  전체 10회 / 최근 10회 → all 기준 3~4위(잘림), recent 기준 압도적 1위
--
-- 즉 topK=2로 좁히면 all 기준 top-2는 {3072, 3033}이라 3153이 누락된다.
-- recent 기준 top-K를 union하지 않으면 갓 급등한 아이템을 후보로도 못 올린다.
DELETE FROM normalized_match_participants;
DELETE FROM champion_item_stats;
DELETE FROM champion_item_rollup;
DELETE FROM champion_pair_item_stats;
DELETE FROM item_meta_stats;

INSERT INTO normalized_match_participants (
    match_id, patch, queue_id, puuid, participant_id, champion_id, team_id, position, win,
    final_core_item_ids, core_item_purchase_order, core_item_purchase_order_complete
) VALUES
    ('B1',  '16.17', 420, 'b1',  1, 157, 100, 'MIDDLE', true,  '6673,3031,3036', '6673,3031,3036', true),
    ('B2',  '16.17', 420, 'b2',  1, 157, 100, 'MIDDLE', true,  '6673,3031,3036', '6673,3031,3036', true),
    ('B3',  '16.17', 420, 'b3',  1, 157, 100, 'MIDDLE', false, '6673,3031,3036', '6673,3031,3036', true),
    ('B4',  '16.17', 420, 'b4',  1, 157, 100, 'MIDDLE', true,  '6673,3031,3006', '6673,3031,3006', true),
    ('B5',  '16.17', 420, 'b5',  1, 157, 100, 'MIDDLE', false, '6673,3031,3006', '6673,3031,3006', true),
    ('B6',  '16.15', 420, 'b6',  1, 157, 100, 'MIDDLE', true,  '6673,3072', '6673,3072', true),
    ('B7',  '16.15', 420, 'b7',  1, 157, 100, 'MIDDLE', true,  '6673,3072', '6673,3072', true),
    ('B8',  '16.15', 420, 'b8',  1, 157, 100, 'MIDDLE', false, '6673,3072', '6673,3072', true),
    ('B9',  '16.15', 420, 'b9',  1, 157, 100, 'MIDDLE', false, '6673,3072', '6673,3072', true),
    ('B10', '16.15', 420, 'b10', 1, 157, 100, 'MIDDLE', true,  '3031,6673', '3031,6673', true);

-- 옛 패치에서만 흔했던 아이템들 (all은 높고 recent는 0)
INSERT INTO normalized_match_participants (
    match_id, patch, queue_id, puuid, participant_id, champion_id, team_id, position, win,
    final_core_item_ids, core_item_purchase_order, core_item_purchase_order_complete
)
SELECT 'OLD72-' || g, '16.15', 420, 'old72-' || g, 1, 157, 100, 'MIDDLE', g % 2 = 0,
       '3072', '3072', true
FROM generate_series(1, 12) g;

INSERT INTO normalized_match_participants (
    match_id, patch, queue_id, puuid, participant_id, champion_id, team_id, position, win,
    final_core_item_ids, core_item_purchase_order, core_item_purchase_order_complete
)
SELECT 'OLD33-' || g, '16.15', 420, 'old33-' || g, 1, 157, 100, 'MIDDLE', g % 2 = 0,
       '3033', '3033', true
FROM generate_series(1, 11) g;

-- 최신 패치에서 급등한 아이템 (all은 중간, recent는 압도적)
INSERT INTO normalized_match_participants (
    match_id, patch, queue_id, puuid, participant_id, champion_id, team_id, position, win,
    final_core_item_ids, core_item_purchase_order, core_item_purchase_order_complete
)
SELECT 'SURGE-' || g, '16.17', 420, 'surge-' || g, 1, 157, 100, 'MIDDLE', g % 2 = 0,
       '3153', '3153', true
FROM generate_series(1, 10) g;
