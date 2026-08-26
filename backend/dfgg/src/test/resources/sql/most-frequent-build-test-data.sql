DELETE FROM normalized_match_participants;

-- 챔피언 222(BOTTOM): '3031,3072' 빌드가 3번, '3006,3031' 빌드가 1번 등장 → 최다빈도는 전자
-- 챔피언 103: Riot 원시값 'MIDDLE'로 저장된다(정규화는 마이닝 시점에만 일어남)
INSERT INTO normalized_match_participants (
    match_id, patch, queue_id, puuid, participant_id, champion_id, team_id, position, tier, win,
    final_core_item_ids, core_item_purchase_order, core_item_purchase_order_complete
) VALUES
    ('KR_M1', '16.16', 420, 'puuid-1', 1, 222, 100, 'BOTTOM', 'PLATINUM', true, '3031,3072', '3031,3072', true),
    ('KR_M2', '16.16', 420, 'puuid-2', 1, 222, 100, 'BOTTOM', 'PLATINUM', false, '3031,3072', '3031,3072', true),
    ('KR_M3', '16.16', 420, 'puuid-3', 1, 222, 100, 'BOTTOM', 'PLATINUM', true, '3031,3072', '3031,3072', true),
    ('KR_M4', '16.16', 420, 'puuid-4', 1, 222, 100, 'BOTTOM', 'PLATINUM', false, '3006,3031', '3006,3031', true),
    ('KR_M5', '16.16', 420, 'puuid-5', 1, 103, 200, 'MIDDLE', 'PLATINUM', true, '3020,3089', '3020,3089', true),
    ('KR_M6', '16.16', 420, 'puuid-6', 1, 103, 200, 'MIDDLE', 'PLATINUM', true, '3020,3089', '3020,3089', true),
    ('KR_M7', '16.16', 420, 'puuid-7', 1, 517, 200, 'UTILITY', 'PLATINUM', true, '3853,3011', '3853,3011', true);
