-- 최근 대회(원본) 하나의 픽을 factor배로 늘린다. 대회 수는 그대로 두고 한 번에 읽는 후보 픽만 늘린다.
-- 복사본 f는 픽 id에 f * 10000을 더하고 경기 시각을 f초 앞당긴다(최근 대회는 그대로 이 대회다). psql -v factor=N 으로 실행한다.
DO $$
BEGIN
    IF (SELECT max(id) FROM pro_picks WHERE id < 1000000) >= 10000 THEN
        RAISE EXCEPTION '원본 픽 id가 10000 이상이라 복사본 id가 겹친다';
    END IF;
END $$;

SELECT tournament_id AS latest FROM pro_picks ORDER BY played_at DESC, tournament_id DESC LIMIT 1 \gset

INSERT INTO pro_picks (id, tournament_id, player_id, source_game_id, played_at, team, side, position, champion_id, win)
SELECT p.id + f * 10000, p.tournament_id, p.player_id, p.source_game_id || ' c' || f,
       p.played_at - f * interval '1 second', p.team, p.side, p.position, p.champion_id, p.win
FROM pro_picks p, generate_series(1, :factor - 1) f
WHERE p.tournament_id = :latest AND p.id < 10000;

INSERT INTO pro_pick_spells (pick_id, slot, spell_id)
SELECT s.pick_id + f * 10000, s.slot, s.spell_id
FROM pro_pick_spells s JOIN pro_picks p ON p.id = s.pick_id, generate_series(1, :factor - 1) f
WHERE p.tournament_id = :latest AND p.id < 10000;

INSERT INTO pro_pick_items (pick_id, slot, item_id)
SELECT i.pick_id + f * 10000, i.slot, i.item_id
FROM pro_pick_items i JOIN pro_picks p ON p.id = i.pick_id, generate_series(1, :factor - 1) f
WHERE p.tournament_id = :latest AND p.id < 10000;

INSERT INTO pro_pick_runes (pick_id, slot, rune_id)
SELECT r.pick_id + f * 10000, r.slot, r.rune_id
FROM pro_pick_runes r JOIN pro_picks p ON p.id = r.pick_id, generate_series(1, :factor - 1) f
WHERE p.tournament_id = :latest AND p.id < 10000;
