-- 현재 패치의 경과일과 커버리지를 한 줄로 낸다. patch-status.sh가 부른다.
--   변수: patch (비우면 숫자 순서로 가장 최근 패치), today (비우면 오늘 KST)
--   출력: patch|previous|started(KST 날짜)|day|champions|coverage100(%)|coverage300(%)
-- 커버리지: 현재·직전 패치에 나온 챔피언 중, 현재 패치의 주 포지션 완료 빌드가 N건 이상인 비율.
-- 완료 빌드와 포지션 정규화는 통계 집계(ChampionItemStatsRepository)와 같은 조건을 쓴다.
WITH patches AS (
    SELECT patch,
           split_part(patch, '.', 1)::int AS major,
           split_part(patch, '.', 2)::int AS minor
    FROM (SELECT DISTINCT patch FROM normalized_match_participants) listed
    WHERE patch ~ '^[0-9]+\.[0-9]+$'
),
current_patch AS (
    SELECT coalesce(
        nullif(:'patch', ''),
        (SELECT patch FROM patches ORDER BY major DESC, minor DESC LIMIT 1)
    ) AS patch
),
previous_patch AS (
    SELECT p.patch
    FROM patches p, patches c
    WHERE c.patch = (SELECT patch FROM current_patch)
      AND (p.major, p.minor) < (c.major, c.minor)
    ORDER BY p.major DESC, p.minor DESC
    LIMIT 1
),
started AS (
    SELECT (min(c.completed_at) AT TIME ZONE 'Asia/Seoul')::date AS day
    FROM stats_aggregation_completions c
    WHERE c.match_id IN (
        SELECT match_id FROM normalized_match_participants
        WHERE patch = (SELECT patch FROM current_patch)
    )
),
builds AS (
    SELECT champion_id,
           CASE position WHEN 'MIDDLE' THEN 'MID' WHEN 'UTILITY' THEN 'SUPPORT' ELSE position END AS position,
           count(*) AS games
    FROM normalized_match_participants
    WHERE patch = (SELECT patch FROM current_patch)
      AND core_item_purchase_order_complete
      AND core_item_purchase_order <> ''
      AND position IN ('TOP', 'JUNGLE', 'MID', 'MIDDLE', 'BOTTOM', 'SUPPORT', 'UTILITY')
    GROUP BY 1, 2
),
main_position AS (
    SELECT champion_id, max(games) AS games FROM builds GROUP BY champion_id
),
champions AS (
    SELECT DISTINCT n.champion_id
    FROM normalized_match_participants n
    WHERE n.patch IN (SELECT patch FROM current_patch UNION ALL SELECT patch FROM previous_patch)
),
coverage AS (
    SELECT count(*) AS champions,
           count(*) FILTER (WHERE coalesce(m.games, 0) >= 100) AS over100,
           count(*) FILTER (WHERE coalesce(m.games, 0) >= 300) AS over300
    FROM champions c
    LEFT JOIN main_position m USING (champion_id)
)
SELECT concat_ws('|',
    (SELECT patch FROM current_patch),
    coalesce((SELECT patch FROM previous_patch), ''),
    coalesce((SELECT day::text FROM started), ''),
    coalesce((coalesce(nullif(:'today', '')::date, (now() AT TIME ZONE 'Asia/Seoul')::date)
              - (SELECT day FROM started) + 1)::text, ''),
    v.champions,
    to_char(round(100.0 * v.over100 / nullif(v.champions, 0), 1), 'FM990.0'),
    to_char(round(100.0 * v.over300 / nullif(v.champions, 0), 1), 'FM990.0'))
FROM coverage v;
