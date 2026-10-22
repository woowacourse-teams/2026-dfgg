-- (대회, 포지션) 픽 조회.
--
-- 대회 1 MID: 픽 101(10-25), 102(10-27), 103(10-26)   ← 조회 대상, played_at 내림차순이면 102·103·101
-- 대회 1 TOP: 픽 104                                   ← 다른 라인
-- 대회 2 MID: 픽 105                                   ← 다른 대회
-- 픽 102의 자식 행은 칸 순서와 다르게 넣는다. 아이템 0번 칸은 비어 있다(행 없음).
DELETE FROM pro_picks;
DELETE FROM pro_players;
DELETE FROM pro_tournaments;

INSERT INTO champions (champion_id, riot_key, name) VALUES
    (9001, 'ProTestChampion', jsonb_build_object('ko-KR', '테스트챔피언', 'en-US', 'Test Champion'))
ON CONFLICT DO NOTHING;
INSERT INTO items (item_id, name) VALUES
    (9001, jsonb_build_object('ko-KR', '아이템1', 'en-US', 'Item 1')),
    (9002, jsonb_build_object('ko-KR', '아이템2', 'en-US', 'Item 2')),
    (9003, jsonb_build_object('ko-KR', '장신구', 'en-US', 'Trinket'))
ON CONFLICT DO NOTHING;
INSERT INTO summoner_spells (spell_id, riot_key, name, icon_key) VALUES
    (9001, 'ProTestFlash', jsonb_build_object('ko-KR', '점멸', 'en-US', 'Test Flash'), 'ProTestFlash.png'),
    (9002, 'ProTestIgnite', jsonb_build_object('ko-KR', '점화', 'en-US', 'Test Ignite'), 'ProTestIgnite.png')
ON CONFLICT DO NOTHING;
INSERT INTO runes (rune_id, tree_id, slot, name, icon_key) VALUES
    (9000, 9000, -1, jsonb_build_object('ko-KR', '트리', 'en-US', 'Tree'), 'perk-images/Styles/T.png'),
    (9001, 9000, 0, jsonb_build_object('ko-KR', '룬1', 'en-US', 'Rune 1'), 'perk-images/Styles/T/R1.png'),
    (9002, 9000, 1, jsonb_build_object('ko-KR', '룬2', 'en-US', 'Rune 2'), 'perk-images/Styles/T/R2.png')
ON CONFLICT DO NOTHING;

INSERT INTO pro_tournaments (id, overview_page, name, league, region, start_date, end_date) VALUES
    (1, '2026 Season World Championship/Main Event', '2026 Worlds Main Event', 'WCS', 'International',
        '2026-10-24', NULL),
    (2, 'LCK/2026 Season/Playoffs', 'LCK 2026 Playoffs', 'LCK', 'Korea', '2026-09-01', NULL);
INSERT INTO pro_players (id, leaguepedia_page, name, team, image_url, updated_at) VALUES
    (1, 'Faker', 'Faker', 'T1', 'https://static.wikia.nocookie.net/faker.png', '2026-10-22 00:00:00+00'),
    (2, 'Chovy', 'Chovy', 'Gen.G', NULL, '2026-10-22 00:00:00+00'),
    (3, 'Zeus', 'Zeus', 'Hanwha Life Esports', NULL, '2026-10-22 00:00:00+00');
INSERT INTO pro_picks (id, tournament_id, player_id, source_game_id, played_at, team, side, position, champion_id,
                       win)
VALUES
    (101, 1, 1, 'g1', '2026-10-25 08:00:00+00', 'T1', 'BLUE', 'MID', 9001, true),
    (102, 1, 2, 'g2', '2026-10-27 08:00:00+00', 'Gen.G', 'RED', 'MID', 9001, false),
    (103, 1, 1, 'g3', '2026-10-26 08:00:00+00', 'T1', 'RED', 'MID', 9001, true),
    (104, 1, 3, 'g1', '2026-10-25 08:00:00+00', 'Hanwha Life Esports', 'BLUE', 'TOP', 9001, true),
    (105, 2, 2, 'g9', '2026-10-28 08:00:00+00', 'Gen.G', 'BLUE', 'MID', 9001, true);

INSERT INTO pro_pick_spells (pick_id, slot, spell_id) VALUES
    (102, 1, 9002), (102, 0, 9001),
    (101, 0, 9001), (101, 1, 9002),
    (103, 0, 9002), (103, 1, 9001);
INSERT INTO pro_pick_items (pick_id, slot, item_id) VALUES
    (102, 6, 9003), (102, 2, 9002), (102, 1, 9001),
    (101, 0, 9001),
    (103, 0, 9002);
INSERT INTO pro_pick_runes (pick_id, slot, rune_id) VALUES
    (102, 8, 9002), (102, 0, 9001), (102, 4, 9002), (102, 1, 9002),
    (101, 0, 9001),
    (103, 0, 9001);
