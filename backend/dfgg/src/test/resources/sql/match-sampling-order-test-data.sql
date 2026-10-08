-- 매치 ID가 시간순으로 증가하고 패치도 함께 올라가는 실제 구조를 재현한다.
-- KR_001~010 = 16.1, KR_011~020 = 16.8, KR_021~030 = 16.16
DELETE FROM normalized_match_participants;

INSERT INTO normalized_match_participants (
    match_id, patch, queue_id, puuid, participant_id, champion_id, team_id, position, tier, win,
    final_core_item_ids, core_item_purchase_order, core_item_purchase_order_complete
)
SELECT 'KR_' || lpad(g::text, 3, '0'),
       CASE WHEN g <= 10 THEN '16.1' WHEN g <= 20 THEN '16.8' ELSE '16.16' END,
       420, 'p-' || g, 1, 157, 100, 'MIDDLE', 'PLATINUM', true, '3031', '3031', true
FROM generate_series(1, 30) g;
