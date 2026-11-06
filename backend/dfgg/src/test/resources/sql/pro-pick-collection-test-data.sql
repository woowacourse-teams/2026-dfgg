-- 대회 픽 수집용 카탈로그. 다른 테스트 데이터와 겹치지 않는 9000번대를 쓴다.
DELETE FROM pro_picks;
DELETE FROM pro_players;
DELETE FROM pro_tournaments;

INSERT INTO champions (champion_id, riot_key, name) VALUES
    (9001, 'ProTestChampion', jsonb_build_object('ko-KR', '테스트챔피언', 'en-US', 'Test Champion')),
    (9002, 'ProOtherChampion', jsonb_build_object('ko-KR', '다른챔피언', 'en-US', 'Other Champion'))
ON CONFLICT DO NOTHING;
INSERT INTO items (item_id, name) VALUES
    (9001, jsonb_build_object('ko-KR', '아이템1', 'en-US', 'Item 1')),
    (9002, jsonb_build_object('ko-KR', '아이템2', 'en-US', 'Item 2'))
ON CONFLICT DO NOTHING;
INSERT INTO summoner_spells (spell_id, riot_key, name, icon_key) VALUES
    (9001, 'ProTestFlash', jsonb_build_object('ko-KR', '점멸', 'en-US', 'Test Flash'), 'ProTestFlash.png'),
    (9002, 'ProTestIgnite', jsonb_build_object('ko-KR', '점화', 'en-US', 'Test Ignite'), 'ProTestIgnite.png')
ON CONFLICT DO NOTHING;
INSERT INTO runes (rune_id, kind, name, tree_id) VALUES
    (9000, 'STYLE', jsonb_build_object('ko-KR', '트리', 'en-US', 'Tree'), 9000),
    (9001, 'PERK', jsonb_build_object('ko-KR', '룬1', 'en-US', 'Rune 1'), 9000),
    (9002, 'PERK', jsonb_build_object('ko-KR', '룬2', 'en-US', 'Rune 2'), 9000)
ON CONFLICT DO NOTHING;
