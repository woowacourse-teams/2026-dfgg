-- 오늘의 추천 픽 한 번의 DB 작업(정규화): 앱이 보내는 쿼리 5개와 같은 모양.
-- 자식 컬렉션은 앱(Hibernate @BatchSize)이 후보 픽 id 목록 IN (1, 2, ...)으로 읽는다.
-- 같은 실행 계획(PK 인덱스)이 나오도록 id 목록을 = ANY(ARRAY(...))로 먼저 만들어 넘긴다. IN (서브쿼리)는 전체 스캔으로 바뀌어 앱과 다르다.
\set p random(1, 5)
SELECT t.id AS tid FROM pro_picks p JOIN pro_tournaments t ON t.id = p.tournament_id
ORDER BY p.played_at DESC, t.id DESC LIMIT 1 \gset
SELECT p.*, pl.*, t.* FROM pro_picks p
JOIN pro_players pl ON pl.id = p.player_id
JOIN pro_tournaments t ON t.id = p.tournament_id
WHERE p.tournament_id = :tid AND p.position = ('{TOP,JUNGLE,MID,BOTTOM,SUPPORT}'::text[])[:p]
ORDER BY p.played_at DESC, p.id DESC;
SELECT pick_id, slot, spell_id FROM pro_pick_spells
WHERE pick_id = ANY(ARRAY(SELECT id FROM pro_picks WHERE tournament_id = :tid
                  AND position = ('{TOP,JUNGLE,MID,BOTTOM,SUPPORT}'::text[])[:p])) ORDER BY pick_id, slot;
SELECT pick_id, slot, item_id FROM pro_pick_items
WHERE pick_id = ANY(ARRAY(SELECT id FROM pro_picks WHERE tournament_id = :tid
                  AND position = ('{TOP,JUNGLE,MID,BOTTOM,SUPPORT}'::text[])[:p])) ORDER BY pick_id, slot;
SELECT pick_id, slot, rune_id FROM pro_pick_runes
WHERE pick_id = ANY(ARRAY(SELECT id FROM pro_picks WHERE tournament_id = :tid
                  AND position = ('{TOP,JUNGLE,MID,BOTTOM,SUPPORT}'::text[])[:p])) ORDER BY pick_id, slot;
