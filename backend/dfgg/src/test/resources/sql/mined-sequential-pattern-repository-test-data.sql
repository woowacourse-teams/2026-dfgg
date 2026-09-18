DELETE FROM mined_sequential_patterns;

INSERT INTO mined_sequential_patterns (
    champion_id, position, tier, patch, pattern_key, items, support_count, scope_total_count, win_count, algorithm_version
) VALUES
    (266, 'TOP', 'GOLD', '14.1', '3071-6653', '3071,6653', 42, 100, 25, 'v1'),
    (266, 'TOP', 'GOLD', '14.1', '3071', '3071', 50, 100, 30, 'v1'),
    (99, 'MID', 'GOLD', '14.1', '4633', '4633', 10, 40, 6, 'v2');
