-- 티어 필터 검증용. 집계는 normalized_match_participants만 읽으므로 다른 표는 두지 않는다.
--
-- 야스오 MID가 같은 아이템(6673,3031)을 EMERALD 2판 / PLATINUM 3판에서 산다.
-- 티어 말고는 모든 조건을 같게 두어야 "걸러진 이유가 티어"임이 분명해진다.
-- 각 판의 쓰레쉬(아군)는 구매 기록이 없다 — pair 통계의 맥락(context)으로만 쓰인다.
DELETE FROM normalized_match_participants;

INSERT INTO normalized_match_participants (
    match_id, patch, queue_id, puuid, participant_id, champion_id, team_id, position, tier, win,
    final_core_item_ids, core_item_purchase_order, core_item_purchase_order_complete
) VALUES
    ('T_E1', '16.17', 420, 'e1', 1, 157, 100, 'MIDDLE',  'EMERALD',  true,  '6673,3031', '6673,3031', true),
    ('T_E2', '16.17', 420, 'e2', 1, 157, 100, 'MIDDLE',  'EMERALD',  false, '6673,3031', '6673,3031', true),
    ('T_P1', '16.17', 420, 'p1', 1, 157, 100, 'MIDDLE',  'PLATINUM', true,  '6673,3031', '6673,3031', true),
    ('T_P2', '16.17', 420, 'p2', 1, 157, 100, 'MIDDLE',  'PLATINUM', true,  '6673,3031', '6673,3031', true),
    ('T_P3', '16.17', 420, 'p3', 1, 157, 100, 'MIDDLE',  'PLATINUM', false, '6673,3031', '6673,3031', true),

    ('T_E1', '16.17', 420, 'e1-ally', 2, 412, 100, 'UTILITY', 'EMERALD',  true,  '', '', false),
    ('T_E2', '16.17', 420, 'e2-ally', 2, 412, 100, 'UTILITY', 'EMERALD',  false, '', '', false),
    ('T_P1', '16.17', 420, 'p1-ally', 2, 412, 100, 'UTILITY', 'PLATINUM', true,  '', '', false),
    ('T_P2', '16.17', 420, 'p2-ally', 2, 412, 100, 'UTILITY', 'PLATINUM', true,  '', '', false),
    ('T_P3', '16.17', 420, 'p3-ally', 2, 412, 100, 'UTILITY', 'PLATINUM', false, '', '', false);
