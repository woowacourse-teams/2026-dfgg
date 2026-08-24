DELETE FROM normalized_match_participants;
DELETE FROM item_tags;
DELETE FROM items;

INSERT INTO normalized_match_participants (
    match_id, patch, queue_id, puuid, participant_id, champion_id, team_id, position, win,
    final_core_item_ids, core_item_purchase_order, core_item_purchase_order_complete
) VALUES
    ('KR_OLD', '14.1', 420, 'puuid-old-ally-1', 1, 1, 100, 'TOP', true, '3071', '3071', true),
    ('KR_OLD', '14.1', 420, 'puuid-old-ally-2', 2, 2, 100, 'TOP', true, '3071', '3071', true),
    ('KR_OLD', '14.1', 420, 'puuid-old-ally-3', 3, 3, 100, 'TOP', true, '3071', '3071', true),
    ('KR_OLD', '14.1', 420, 'puuid-old-ally-4', 4, 4, 100, 'TOP', true, '3071', '3071', true),
    ('KR_OLD', '14.1', 420, 'puuid-old-ally-5', 5, 5, 100, 'TOP', true, '3071', '3071', true),
    ('KR_OLD', '14.1', 420, 'puuid-old-enemy-6', 6, 6, 200, 'TOP', false, '3020', '3020', true),
    ('KR_OLD', '14.1', 420, 'puuid-old-enemy-7', 7, 7, 200, 'TOP', false, '3020', '3020', true),
    ('KR_OLD', '14.1', 420, 'puuid-old-enemy-8', 8, 8, 200, 'TOP', false, '3020', '3020', true),
    ('KR_OLD', '14.1', 420, 'puuid-old-enemy-9', 9, 9, 200, 'TOP', false, '3020', '3020', true),
    ('KR_OLD', '14.1', 420, 'puuid-old-enemy-10', 10, 10, 200, 'TOP', false, '3020', '3020', true),
    ('KR_NEW', '14.1', 420, 'puuid-new-ally-90', 90, 90, 100, 'TOP', true, '3071', '3071', true),
    ('KR_NEW', '14.1', 420, 'puuid-new-ally-91', 91, 91, 100, 'TOP', true, '3071', '3071', true),
    ('KR_NEW', '14.1', 420, 'puuid-new-ally-92', 92, 92, 100, 'TOP', true, '3071', '3071', true),
    ('KR_NEW', '14.1', 420, 'puuid-new-ally-93', 93, 93, 100, 'TOP', true, '3071', '3071', true),
    ('KR_NEW', '14.1', 420, 'puuid-new-ally-94', 94, 94, 100, 'TOP', true, '3071', '3071', true),
    ('KR_NEW', '14.1', 420, 'puuid-new-enemy-95', 95, 95, 200, 'TOP', false, '3020', '3020', true),
    ('KR_NEW', '14.1', 420, 'puuid-new-enemy-96', 96, 96, 200, 'TOP', false, '3020', '3020', true),
    ('KR_NEW', '14.1', 420, 'puuid-new-enemy-97', 97, 97, 200, 'TOP', false, '3020', '3020', true),
    ('KR_NEW', '14.1', 420, 'puuid-new-enemy-98', 98, 98, 200, 'TOP', false, '3020', '3020', true),
    ('KR_NEW', '14.1', 420, 'puuid-new-enemy-99', 99, 99, 200, 'TOP', false, '3020', '3020', true);
