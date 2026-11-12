-- 대회 픽 제약 검증용 최소 데이터. 카탈로그 행은 다른 테스트 데이터와 겹치지 않는 9000번대를 쓴다.
DELETE FROM pro_picks;
DELETE FROM pro_players;
DELETE FROM pro_tournaments;

INSERT INTO champions (champion_id, riot_key, name) VALUES
    (9001, 'ProTestChampion', jsonb_build_object('ko-KR', '테스트챔피언', 'en-US', 'Test Champion'))
ON CONFLICT DO NOTHING;
INSERT INTO items (item_id, name) VALUES
    (9001, jsonb_build_object('ko-KR', '테스트아이템', 'en-US', 'Test Item'))
ON CONFLICT DO NOTHING;
INSERT INTO summoner_spells (spell_id, riot_key, name, icon_key) VALUES
    (9001, 'ProTestSpell', jsonb_build_object('ko-KR', '테스트주문', 'en-US', 'Test Spell'), 'ProTestSpell.png')
ON CONFLICT DO NOTHING;
INSERT INTO runes (rune_id, kind, name, tree_id) VALUES
    (9000, 'STYLE', jsonb_build_object('ko-KR', '테스트트리', 'en-US', 'Test Tree'), 9000),
    (9001, 'PERK', jsonb_build_object('ko-KR', '테스트룬', 'en-US', 'Test Rune'), 9000)
ON CONFLICT DO NOTHING;

INSERT INTO pro_tournaments (id, overview_page, name, league, region, start_date, end_date) VALUES
    (1, '2026 Season World Championship/Main Event', '2026 Worlds Main Event', 'WCS', 'International',
        '2026-10-24', '2026-11-08');
INSERT INTO pro_players (id, leaguepedia_page, name, team, image_url, updated_at) VALUES
    (1, 'Faker', 'Faker', 'T1', NULL, '2026-10-22 00:00:00+00'),
    (2, 'Kratos (Ngô Đức Khánh)', 'Kratos', 'Team Secret Whales', NULL, '2026-10-22 00:00:00+00');
