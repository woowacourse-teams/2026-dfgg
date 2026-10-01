-- v3 Build 전이 쿼리(findNextItemAfterExactPrefix·findNextItemAfterLastItem) 전용 커버링 인덱스.
--
-- 기존 idx_nmp_champion_position_patch는 행을 찾기만 하고, 구매 순서·승패를 가져오려면 행마다 힙을 간다.
-- 쿼리가 읽는 열을 INCLUDE에 담아 index-only scan으로 바꾼다 → 같은 조회가 317블록. 쿼리·결과는 그대로다.
--
-- CONCURRENTLY: 수집 INSERT를 막지 않는다. 트랜잭션 안에서는 실행할 수 없는데,
-- Flyway가 이 문장을 감지해 이 파일만 트랜잭션 밖에서 실행한다 — 그래서 이 파일에 다른 문장을 섞지 않는다.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_nmp_build_transition
    ON normalized_match_participants (champion_id, position)
    INCLUDE (core_item_purchase_order, patch, win)
    WHERE core_item_purchase_order_complete;
