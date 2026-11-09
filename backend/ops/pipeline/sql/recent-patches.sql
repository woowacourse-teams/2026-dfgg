-- 앱이 기동할 때 읽는 최근 패치 창과 같은 목록을 한 줄로 낸다(숫자 순서로 최근 :size개, 쉼표 구분).
-- 복제 전후로 이 값이 바뀌면 앱이 메모리에 둔 패치 창이 낡은 것이므로 재기동한다.
SELECT coalesce(string_agg(patch, ',' ORDER BY major DESC, minor DESC), '')
FROM (
    SELECT patch, split_part(patch, '.', 1)::int AS major, split_part(patch, '.', 2)::int AS minor
    FROM (SELECT DISTINCT patch FROM normalized_match_participants) listed
    WHERE patch ~ '^[0-9]+\.[0-9]+$'
    ORDER BY major DESC, minor DESC
    LIMIT :size
) recent;
