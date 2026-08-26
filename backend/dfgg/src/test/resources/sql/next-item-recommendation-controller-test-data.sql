DELETE FROM normalized_match_participants;
DELETE FROM champions;
DELETE FROM items;

INSERT INTO champions (champion_id, riot_key, name) VALUES
    (222, 'Jinx', '징크스'),
    (100, 'AllyChamp', '아군챔프'),
    (200, 'EnemyChamp', '적챔프');

INSERT INTO items (item_id, name, tags) VALUES
    (3072, '루난의 허리케인', '[]'::jsonb),
    (3006, '광전사의 군화', '[]'::jsonb);

-- 챔피언 222(BOTTOM)이 '3072,3006'을 3번, '3006' 하나만 1번 산 것으로 시딩해
-- 최다빈도 빌드가 '3072,3006'이 되도록 한다. mined_sequential_patterns/embeddings/
-- composition_stats는 비워둬(①④단계가 빈 결과를 내도록) ⑤단계까지 폴백이 실제로
-- 내려가는지 검증한다.
INSERT INTO normalized_match_participants (
    match_id, patch, queue_id, puuid, participant_id, champion_id, team_id, position, tier, win,
    final_core_item_ids, core_item_purchase_order, core_item_purchase_order_complete
) VALUES
    ('KR_N1', '16.16', 420, 'puuid-n1', 1, 222, 100, 'BOTTOM', 'PLATINUM', true, '3072,3006', '3072,3006', true),
    ('KR_N2', '16.16', 420, 'puuid-n2', 1, 222, 100, 'BOTTOM', 'PLATINUM', false, '3072,3006', '3072,3006', true),
    ('KR_N3', '16.16', 420, 'puuid-n3', 1, 222, 100, 'BOTTOM', 'PLATINUM', true, '3072,3006', '3072,3006', true),
    ('KR_N4', '16.16', 420, 'puuid-n4', 1, 222, 100, 'BOTTOM', 'PLATINUM', false, '3006', '3006', true);
