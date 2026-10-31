-- (아리, MID)는 일부러 rank 순서와 다르게 넣는다. 조회가 넣은 순서가 아니라 rank로 정렬하는지 본다.
--
-- (103 아리, MID): 3위 127 리산드라 · 1위 90 말자하 · 2위 1 애니
-- (103 아리, TOP): 1위 86 가렌        ← 다른 라인
-- (238 제드, MID): 1위 90 말자하      ← 다른 적
DELETE FROM champion_counters;

INSERT INTO champion_counters (enemy_champion_id, position, counter_champion_id, rank) VALUES
    (103, 'MID', 127, 3),
    (103, 'MID',  90, 1),
    (103, 'MID',   1, 2),
    (103, 'TOP',  86, 1),
    (238, 'MID',  90, 1);
