-- 수집한 대회 픽(원본)을 copies배로 늘린다. 복사본 k는 대회·픽 id에 오프셋을 더하고, 경기 시각을 k주 앞당긴다.
-- 선수는 그대로 공유한다(시즌이 바뀌어도 같은 선수가 뛴다). psql -v copies=N 으로 실행한다.
INSERT INTO pro_tournaments (id, overview_page, name, league, region, start_date, end_date)
SELECT t.id + k * 100000, t.overview_page || ' #' || k, t.name, t.league, t.region,
       t.start_date - k * 7, t.end_date - k * 7
FROM pro_tournaments t, generate_series(1, :copies - 1) k
WHERE t.id < 100000;

INSERT INTO pro_picks (id, tournament_id, player_id, source_game_id, played_at, team, side, position, champion_id, win)
SELECT p.id + k * 1000000, p.tournament_id + k * 100000, p.player_id, p.source_game_id || ' #' || k,
       p.played_at - k * interval '7 days', p.team, p.side, p.position, p.champion_id, p.win
FROM pro_picks p, generate_series(1, :copies - 1) k
WHERE p.id < 1000000;

INSERT INTO pro_pick_spells (pick_id, slot, spell_id)
SELECT s.pick_id + k * 1000000, s.slot, s.spell_id
FROM pro_pick_spells s, generate_series(1, :copies - 1) k WHERE s.pick_id < 1000000;

INSERT INTO pro_pick_items (pick_id, slot, item_id)
SELECT i.pick_id + k * 1000000, i.slot, i.item_id
FROM pro_pick_items i, generate_series(1, :copies - 1) k WHERE i.pick_id < 1000000;

INSERT INTO pro_pick_runes (pick_id, slot, rune_id)
SELECT r.pick_id + k * 1000000, r.slot, r.rune_id
FROM pro_pick_runes r, generate_series(1, :copies - 1) k WHERE r.pick_id < 1000000;
