DELETE FROM normalized_match_participants;

INSERT INTO normalized_match_participants (
    match_id, patch, queue_id, puuid, participant_id, champion_id, team_id, position, win,
    final_core_item_ids, core_item_purchase_order, core_item_purchase_order_complete
) VALUES
    ('KR_G', '14.1', 420, 'puuid-g-1', 1, 1, 100, 'TOP', true, '3071', '3071', true),
    ('KR_B', '14.1', 420, 'puuid-b-1', 1, 1, 100, 'TOP', true, '3071', '3071', true),
    ('KR_B', '14.1', 420, 'puuid-b-2', 2, 2, 100, 'TOP', true, '3071', '3071', true),
    ('KR_E', '14.1', 420, 'puuid-e-1', 1, 1, 100, 'TOP', true, '3071', '3071', true),
    ('KR_A', '14.1', 420, 'puuid-a-1', 1, 1, 100, 'TOP', true, '3071', '3071', true),
    ('KR_D', '14.1', 420, 'puuid-d-1', 1, 1, 100, 'TOP', true, '3071', '3071', true),
    ('KR_D', '14.1', 420, 'puuid-d-2', 2, 2, 100, 'TOP', true, '3071', '3071', true),
    ('KR_C', '14.1', 420, 'puuid-c-1', 1, 1, 100, 'TOP', true, '3071', '3071', true),
    ('KR_F', '14.1', 420, 'puuid-f-1', 1, 1, 100, 'TOP', true, '3071', '3071', true);
