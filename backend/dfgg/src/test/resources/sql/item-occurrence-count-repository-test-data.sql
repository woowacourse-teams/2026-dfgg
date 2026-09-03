DELETE FROM normalized_match_participants;

INSERT INTO normalized_match_participants (
    match_id, patch, queue_id, puuid, participant_id, champion_id, team_id, position, win,
    final_core_item_ids, core_item_purchase_order, core_item_purchase_order_complete
) VALUES
    ('KR_FREQ_1', '14.1', 420, 'puuid-1', 1, 1, 100, 'TOP', true, '1001', '1001', true),
    ('KR_FREQ_2', '14.1', 420, 'puuid-2', 1, 1, 100, 'TOP', true, '1001', '1001', true),
    ('KR_FREQ_3', '14.1', 420, 'puuid-3', 1, 1, 100, 'TOP', true, '1001,2002', '1001,2002', true),
    ('KR_FREQ_4', '14.1', 420, 'puuid-4', 1, 1, 100, 'TOP', true, '2002', '2002', true);
