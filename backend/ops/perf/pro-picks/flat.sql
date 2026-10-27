-- 오늘의 추천 픽 한 번의 DB 작업(단일 테이블): 쿼리 2개.
\set p random(1, 5)
SELECT tournament_id AS tid FROM pro_picks_flat ORDER BY played_at DESC, tournament_id DESC LIMIT 1 \gset
SELECT * FROM pro_picks_flat
WHERE tournament_id = :tid AND position = ('{TOP,JUNGLE,MID,BOTTOM,SUPPORT}'::text[])[:p]
ORDER BY played_at DESC, id DESC;
