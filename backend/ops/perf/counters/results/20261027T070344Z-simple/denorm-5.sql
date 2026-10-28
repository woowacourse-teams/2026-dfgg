SELECT champion_id FROM champions WHERE champion_id = ANY('{1,3,4,7,8}'::bigint[]);
SELECT * FROM counter_view WHERE enemy_champion_id = ANY('{1,3,4,7,8}'::int[]) AND position = 'MID';
