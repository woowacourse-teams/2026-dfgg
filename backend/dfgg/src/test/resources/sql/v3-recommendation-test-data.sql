-- v3 파이프라인 엔드투엔드용. Build 픽스처에 챔피언·아이템 메타데이터를 더한다.
DELETE FROM normalized_match_participants;
DELETE FROM champion_item_stats;
DELETE FROM champion_item_rollup;
DELETE FROM champion_pair_item_stats;
DELETE FROM item_meta_stats;
DELETE FROM champions;
DELETE FROM items;

INSERT INTO champions (champion_id, riot_key, name) VALUES
    (157, 'Yasuo', '야스오'),
    (222, 'Jinx', '징크스'), (412, 'Thresh', '쓰레쉬'), (64, 'LeeSin', '리신'), (516, 'Ornn', '오른'),
    (33, 'Rammus', '람머스'), (103, 'Ahri', '아리'), (51, 'Caitlyn', '케이틀린'),
    (89, 'Leona', '레오나'), (60, 'Elise', '엘리스');

INSERT INTO items (item_id, name, tags) VALUES
    (6673, '몰락한 왕의 검', '["Damage"]'::jsonb),
    (3031, '무한의 대검', '["Damage","CriticalStrike"]'::jsonb),
    (3036, '도미닉 경의 인사', '["Damage","ArmorPenetration"]'::jsonb),
    (3072, '피바라기', '["Damage","LifeSteal"]'::jsonb),
    (3033, '필멸자의 운명', '["Damage"]'::jsonb),
    (3153, '몰락한 왕의 검(급등)', '["Damage","AttackSpeed"]'::jsonb),
    (3006, '광전사의 군화', '["Boots"]'::jsonb),
    (3047, '판금 장화', '["Boots"]'::jsonb);

-- 전개 표본: [6673,3031] 다음은 3036(3판) 또는 3006(2판)
INSERT INTO normalized_match_participants (
    match_id, patch, queue_id, puuid, participant_id, champion_id, team_id, position, win,
    final_core_item_ids, core_item_purchase_order, core_item_purchase_order_complete
) VALUES
    ('V1', '16.17', 420, 'v1', 1, 157, 100, 'MIDDLE', true,  '6673,3031,3036', '6673,3031,3036', true),
    ('V2', '16.17', 420, 'v2', 1, 157, 100, 'MIDDLE', true,  '6673,3031,3036', '6673,3031,3036', true),
    ('V3', '16.17', 420, 'v3', 1, 157, 100, 'MIDDLE', false, '6673,3031,3036', '6673,3031,3036', true),
    ('V4', '16.17', 420, 'v4', 1, 157, 100, 'MIDDLE', true,  '6673,3031,3006', '6673,3031,3006', true),
    ('V5', '16.17', 420, 'v5', 1, 157, 100, 'MIDDLE', false, '6673,3031,3006', '6673,3031,3006', true);

-- 챔피언 단위 백오프용 표본. 신발 두 종(3006/3047)이 모두 등장하도록 둔다.
INSERT INTO normalized_match_participants (
    match_id, patch, queue_id, puuid, participant_id, champion_id, team_id, position, win,
    final_core_item_ids, core_item_purchase_order, core_item_purchase_order_complete
)
SELECT 'VC-' || g, '16.17', 420, 'vc-' || g, 1, 157, 100, 'MIDDLE', g % 2 = 0,
       '3047,3072', '3047,3072', true
FROM generate_series(1, 6) g;
