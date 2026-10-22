-- 오늘의 추천 픽.
--
-- 대회 1 (최근: 마지막 경기 10-27)  MID 201 Faker 9001 · MID 202 Chovy 9002 · TOP 203 Zeus 9001
-- 대회 2 (예전: 마지막 경기 10-20)  MID 204 Faker 9002            ← 최근 대회가 아니라 뽑히지 않는다
-- 픽 201: 주문 점멸·점화, 아이템 0번·장신구, 룬 핵심·주 1·보조 1·파편 2 (5008·5011은 V13 고정 행)
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
INSERT INTO runes (rune_id, tree_id, slot, name, icon_key) VALUES
    (9000, 9000, -1, jsonb_build_object('ko-KR', '트리', 'en-US', 'Tree'), 'perk-images/Styles/T.png'),
    (9001, 9000, 0, jsonb_build_object('ko-KR', '룬1', 'en-US', 'Rune 1'), 'perk-images/Styles/T/R1.png'),
    (9002, 9000, 1, jsonb_build_object('ko-KR', '룬2', 'en-US', 'Rune 2'), 'perk-images/Styles/T/R2.png'),
    (9100, 9100, -1, jsonb_build_object('ko-KR', '보조트리', 'en-US', 'Second Tree'), 'perk-images/Styles/S.png'),
    (9101, 9100, 1, jsonb_build_object('ko-KR', '보조룬', 'en-US', 'Second Rune'), 'perk-images/Styles/S/R.png')
ON CONFLICT DO NOTHING;

INSERT INTO pro_tournaments (id, overview_page, name, league, region, start_date, end_date) VALUES
    (1, '2026 Season World Championship/Main Event', '2026 Worlds Main Event', 'World Championship',
        'International', '2026-10-24', NULL),
    (2, 'LCK/2026 Season/Playoffs', 'LCK 2026 Playoffs', 'LCK', 'Korea', '2026-09-01', NULL);
INSERT INTO pro_players (id, leaguepedia_page, name, team, image_url, updated_at) VALUES
    (1, 'Faker', 'Faker', 'T1', 'https://static.wikia.nocookie.net/faker.png', '2026-10-22 00:00:00+00'),
    (2, 'Chovy', 'Chovy', 'Gen.G', NULL, '2026-10-22 00:00:00+00'),
    (3, 'Zeus', 'Zeus', 'Hanwha Life Esports', NULL, '2026-10-22 00:00:00+00');
INSERT INTO pro_picks (id, tournament_id, player_id, source_game_id, played_at, team, side, position, champion_id,
                       win)
VALUES
    (201, 1, 1, 'w1', '2026-10-27 08:00:00+00', 'T1', 'BLUE', 'MID', 9001, true),
    (202, 1, 2, 'w1', '2026-10-27 08:00:00+00', 'Gen.G', 'RED', 'MID', 9002, false),
    (203, 1, 3, 'w2', '2026-10-26 08:00:00+00', 'Hanwha Life Esports', 'BLUE', 'TOP', 9001, true),
    (204, 2, 1, 'l1', '2026-10-20 08:00:00+00', 'T1', 'BLUE', 'MID', 9002, true);

INSERT INTO pro_pick_spells (pick_id, slot, spell_id) VALUES (201, 0, 9001), (201, 1, 9002);
INSERT INTO pro_pick_items (pick_id, slot, item_id) VALUES (201, 6, 9002), (201, 0, 9001);
INSERT INTO pro_pick_runes (pick_id, slot, rune_id) VALUES
    (201, 0, 9001), (201, 1, 9002), (201, 4, 9101), (201, 6, 5008), (201, 8, 5011);
