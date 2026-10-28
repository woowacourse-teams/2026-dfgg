-- 비교용 비정규화 테이블. (상대 챔피언, 내 라인)마다 응답에 필요한 것을 한 행에 미리 모아 둔다.
-- 카운터가 없는 상대도 이름·라인 분포는 나가야 하므로 모든 챔피언 × 다섯 라인 행을 만든다.
-- psql -v patch='16.18' 로 실행한다.
DROP TABLE IF EXISTS counter_view;
CREATE TABLE counter_view AS
WITH lanes(position) AS (VALUES ('TOP'), ('JUNGLE'), ('MID'), ('BOTTOM'), ('SUPPORT')),
-- 비율을 먼저 계산하고(창 함수) 그다음 챔피언별로 묶는다. 집계 안에 창 함수를 쓸 수 없다.
shares AS (
    SELECT s.champion_id, s.position, s.games,
           round(s.games * 100.0 / sum(s.games) OVER (PARTITION BY s.champion_id), 1) AS rate
    FROM champion_position_stats s
    WHERE s.patch = :'patch'
),
shares_by_champion AS (
    SELECT champion_id,
           jsonb_agg(jsonb_build_object('position', position, 'rate', rate) ORDER BY games DESC) AS position_shares
    FROM shares
    GROUP BY champion_id
)
SELECT c.champion_id::int AS enemy_champion_id,
       l.position,
       c.name AS enemy_name,
       :'patch'::varchar AS patch,
       coalesce(sh.position_shares, '[]'::jsonb) AS position_shares,
       coalesce((SELECT jsonb_agg(jsonb_build_object('id', cc.counter_champion_id, 'name', k.name) ORDER BY cc.rank)
                 FROM champion_counters cc JOIN champions k ON k.champion_id = cc.counter_champion_id
                 WHERE cc.enemy_champion_id = c.champion_id AND cc.position = l.position), '[]'::jsonb) AS counters
FROM champions c
CROSS JOIN lanes l
LEFT JOIN shares_by_champion sh ON sh.champion_id = c.champion_id;

ALTER TABLE counter_view ADD PRIMARY KEY (enemy_champion_id, position);
