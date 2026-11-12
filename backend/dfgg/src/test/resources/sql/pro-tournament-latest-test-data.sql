-- 최근 대회 = 픽의 played_at이 가장 늦은 대회. 시작일이나 id 순서가 아니다.
--
-- 1 월즈 Main Event (시작 10-24)  픽 10-25
-- 2 LCK 플레이오프   (시작 09-01)  픽 10-26   ← 시작은 가장 이르지만 마지막 경기가 가장 늦다
-- 3 월즈 결승 예정   (시작 11-08)  픽 없음    ← 시작일은 가장 늦지만 경기가 없다
DELETE FROM pro_picks;
DELETE FROM pro_players;
DELETE FROM pro_tournaments;

INSERT INTO champions (champion_id, riot_key, name) VALUES
    (9001, 'ProTestChampion', jsonb_build_object('ko-KR', '테스트챔피언', 'en-US', 'Test Champion'))
ON CONFLICT DO NOTHING;

INSERT INTO pro_tournaments (id, overview_page, name, league, region, start_date, end_date) VALUES
    (1, '2026 Season World Championship/Main Event', '2026 Worlds Main Event', 'WCS', 'International',
        '2026-10-24', NULL),
    (2, 'LCK/2026 Season/Playoffs', 'LCK 2026 Playoffs', 'LCK', 'Korea', '2026-09-01', NULL),
    (3, '2026 Season World Championship/Finals', '2026 Worlds Finals', 'WCS', 'International',
        '2026-11-08', NULL);
INSERT INTO pro_players (id, leaguepedia_page, name, team, image_url, updated_at) VALUES
    (1, 'Faker', 'Faker', 'T1', NULL, '2026-10-22 00:00:00+00');
INSERT INTO pro_picks (tournament_id, player_id, source_game_id, played_at, team, side, position, champion_id, win)
VALUES
    (1, 1, 'worlds-game', '2026-10-25 08:00:00+00', 'T1', 'BLUE', 'MID', 9001, true),
    (2, 1, 'lck-game', '2026-10-26 08:00:00+00', 'T1', 'RED', 'MID', 9001, false);
