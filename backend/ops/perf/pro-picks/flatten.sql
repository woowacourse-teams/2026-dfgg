-- 비교용 단일 테이블. 픽 한 행에 대회·선수 정보와 주문·아이템·룬 id 배열(칸 순서, 빈 칸은 NULL)을 함께 둔다.
-- 이름·이미지는 정규화와 똑같이 카탈로그에서 읽으므로 여기 넣지 않는다.
DROP TABLE IF EXISTS pro_picks_flat;
CREATE TABLE pro_picks_flat AS
SELECT p.id, p.tournament_id, t.name AS tournament_name, t.league, t.start_date,
       pl.name AS player_name, p.team, pl.image_url,
       p.played_at, p.side, p.position, p.champion_id, p.win,
       (SELECT array_agg(s.spell_id ORDER BY s.slot) FROM pro_pick_spells s WHERE s.pick_id = p.id) AS spell_ids,
       (SELECT array_agg(i.item_id ORDER BY g.slot)
          FROM generate_series(0, 6) g(slot)
          LEFT JOIN pro_pick_items i ON i.pick_id = p.id AND i.slot = g.slot) AS item_ids,
       (SELECT array_agg(r.rune_id ORDER BY g.slot)
          FROM generate_series(0, 8) g(slot)
          LEFT JOIN pro_pick_runes r ON r.pick_id = p.id AND r.slot = g.slot) AS rune_ids
FROM pro_picks p
JOIN pro_tournaments t ON t.id = p.tournament_id
JOIN pro_players pl ON pl.id = p.player_id;

ALTER TABLE pro_picks_flat ADD PRIMARY KEY (id);
-- 정규화 테이블과 같은 인덱스를 둔다.
CREATE INDEX idx_flat_played_at ON pro_picks_flat (played_at DESC);
CREATE INDEX idx_flat_tournament_position ON pro_picks_flat (tournament_id, position, played_at DESC);
