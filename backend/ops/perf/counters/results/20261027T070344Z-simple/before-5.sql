SELECT champion_id FROM champions WHERE champion_id = ANY('{1,3,4,7,8}'::bigint[]);
SELECT DISTINCT patch FROM champion_position_stats;
SELECT * FROM champion_position_stats WHERE patch = '16.18' AND champion_id = ANY('{1,3,4,7,8}'::int[]);
SELECT * FROM champion_counters WHERE enemy_champion_id = 1 AND position = 'MID' ORDER BY rank;
SELECT * FROM champion_counters WHERE enemy_champion_id = 3 AND position = 'MID' ORDER BY rank;
SELECT * FROM champion_counters WHERE enemy_champion_id = 4 AND position = 'MID' ORDER BY rank;
SELECT * FROM champion_counters WHERE enemy_champion_id = 7 AND position = 'MID' ORDER BY rank;
SELECT * FROM champion_counters WHERE enemy_champion_id = 8 AND position = 'MID' ORDER BY rank;
SELECT * FROM champions WHERE champion_id = ANY(ARRAY(
    SELECT counter_champion_id::bigint FROM champion_counters
    WHERE enemy_champion_id = ANY('{1,3,4,7,8}'::int[]) AND position = 'MID'
    UNION SELECT unnest('{1,3,4,7,8}'::bigint[])));
