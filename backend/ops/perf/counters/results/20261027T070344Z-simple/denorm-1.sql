SELECT champion_id FROM champions WHERE champion_id = ANY('{1}'::bigint[]);
SELECT * FROM counter_view WHERE enemy_champion_id = ANY('{1}'::int[]) AND position = 'MID';
