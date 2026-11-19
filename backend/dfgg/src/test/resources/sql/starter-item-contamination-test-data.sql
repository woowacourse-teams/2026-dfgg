-- #297 재현: 09-17~10-17에 정규화된 구매 순서에는 최종 인벤토리에 남은 시작 아이템이 Core처럼 섞였다.
-- v3-recommendation-test-data.sql 뒤에 실행한다.
--
-- 도란의 검(1055)은 조합 재료가 없는 시작 아이템이라 Core가 아니다. 0분에 사므로 구매 순서 첫 자리에 온다.
--   야스오(157) MIDDLE 12판 '1055,6673'  → 아무것도 안 산 요청의 첫 구매 1위가 도란이 된다
INSERT INTO items (item_id, name, tags) VALUES
    (1055, jsonb_build_object('ko-KR', '도란의 검', 'en-US', 'Doran''s Blade'), '["Damage","Lane"]'::jsonb);

INSERT INTO normalized_match_participants (
    match_id, patch, queue_id, puuid, participant_id, champion_id, team_id, position, win, tier,
    final_core_item_ids, core_item_purchase_order, core_item_purchase_order_complete
)
SELECT 'DORAN-' || g, '16.17', 420, 'doran-' || g, 1, 157, 100, 'MIDDLE', g % 2 = 0, 'EMERALD',
       '1055,6673', '1055,6673', true
FROM generate_series(1, 12) g;
