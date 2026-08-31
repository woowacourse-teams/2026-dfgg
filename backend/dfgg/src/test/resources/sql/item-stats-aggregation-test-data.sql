-- 손으로 검증 가능한 최소 픽스처.
-- 패치 3종(16.15 / 16.17), 윈도 크기 1이면 recent = {16.17}.
--
-- M1(16.15)                          M2(16.17)                     M3(16.17)
--  team100: 157 MIDDLE  win  6673,3031   team100: 157 MIDDLE lose 6673   team100: 157 TOP  win  6673
--  team100: 222 BOTTOM  win  3031        team100: 222 BOTTOM lose 6672   team200: 33 MIDDLE lose 3068
--  team100: 412 UTILITY win  3190        team200: 33  TOP    win  3068,3075
--  team200: 33  TOP     lose 3068        team200: 103 MIDDLE win  6653
--  team200: 103 MIDDLE  lose 6653
--  team100: 999 JUNGLE  win  3071  ← core_item_purchase_order_complete = false
DELETE FROM normalized_match_participants;

INSERT INTO normalized_match_participants (
    match_id, patch, queue_id, puuid, participant_id, champion_id, team_id, position, win,
    final_core_item_ids, core_item_purchase_order, core_item_purchase_order_complete
) VALUES
    ('M1', '16.15', 420, 'm1-a', 1, 157, 100, 'MIDDLE',  true,  '6673,3031', '6673,3031', true),
    ('M1', '16.15', 420, 'm1-b', 2, 222, 100, 'BOTTOM',  true,  '3031',      '3031',      true),
    ('M1', '16.15', 420, 'm1-c', 3, 412, 100, 'UTILITY', true,  '3190',      '3190',      true),
    ('M1', '16.15', 420, 'm1-d', 4,  33, 200, 'TOP',     false, '3068',      '3068',      true),
    ('M1', '16.15', 420, 'm1-e', 5, 103, 200, 'MIDDLE',  false, '6653',      '6653',      true),
    ('M1', '16.15', 420, 'm1-f', 6, 999, 100, 'JUNGLE',  true,  '3071',      '3071',      false),

    ('M2', '16.17', 420, 'm2-a', 1, 157, 100, 'MIDDLE',  false, '6673',      '6673',      true),
    ('M2', '16.17', 420, 'm2-b', 2, 222, 100, 'BOTTOM',  false, '6672',      '6672',      true),
    ('M2', '16.17', 420, 'm2-c', 3,  33, 200, 'TOP',     true,  '3068,3075', '3068,3075', true),
    ('M2', '16.17', 420, 'm2-d', 4, 103, 200, 'MIDDLE',  true,  '6653',      '6653',      true),

    ('M3', '16.17', 420, 'm3-a', 1, 157, 100, 'TOP',     true,  '6673',      '6673',      true),
    ('M3', '16.17', 420, 'm3-b', 2,  33, 200, 'MIDDLE',  false, '3068',      '3068',      true);
