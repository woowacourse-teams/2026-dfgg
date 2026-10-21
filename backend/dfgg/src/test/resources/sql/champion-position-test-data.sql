-- 현재 패치는 16.10이다 (문자열로 고르면 16.9가 뽑힌다).
--
-- M1(16.9)  103 MIDDLE              ← 이전 패치라 제외
-- M2(16.10) 103 MIDDLE · 412 UTILITY · 64 JUNGLE · 22 ''(빈 포지션)
-- M3(16.10) 103 MIDDLE · 7 NULL(포지션 없음)
-- M4(16.10) 103 TOP
--
-- 기대: (103, MID, 2) · (103, TOP, 1) · (412, SUPPORT, 1) · (64, JUNGLE, 1)
DELETE FROM champion_position_stats;
DELETE FROM normalized_match_participants;

INSERT INTO normalized_match_participants (
    match_id, patch, queue_id, puuid, participant_id, champion_id, team_id, position, win,
    final_core_item_ids, core_item_purchase_order, core_item_purchase_order_complete
) VALUES
    ('M1', '16.9',  420, 'm1-a', 1, 103, 100, 'MIDDLE',  true,  '', '', true),

    ('M2', '16.10', 420, 'm2-a', 1, 103, 100, 'MIDDLE',  true,  '', '', true),
    ('M2', '16.10', 420, 'm2-b', 2, 412, 100, 'UTILITY', true,  '', '', true),
    ('M2', '16.10', 420, 'm2-c', 3,  64, 200, 'JUNGLE',  false, '', '', true),
    ('M2', '16.10', 420, 'm2-d', 4,  22, 200, '',        false, '', '', true),

    ('M3', '16.10', 420, 'm3-a', 1, 103, 200, 'MIDDLE',  false, '', '', true),
    ('M3', '16.10', 420, 'm3-b', 2,   7, 100, NULL,      true,  '', '', true),

    ('M4', '16.10', 420, 'm4-a', 1, 103, 100, 'TOP',     true,  '', '', true);
