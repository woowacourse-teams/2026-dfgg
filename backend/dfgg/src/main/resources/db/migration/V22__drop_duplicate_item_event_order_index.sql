-- V10의 idx_participant_item_event_match_order는 uk_participant_item_event_order와 컬럼·순서가 같은 중복 인덱스다.
-- 조회는 유니크 제약 인덱스가 그대로 받치므로, 구매 이벤트 INSERT마다 갱신할 인덱스 하나와 운영 1,620MB를 덜어낸다.
-- V21에서 인덱스를 하나 더했으므로 이 삭제로 인덱스 개수를 5개로 되돌린다.
--
-- CONCURRENTLY: 삭제 중에도 조회·INSERT를 막지 않는다. V21과 같은 이유로 이 파일에 다른 문장을 섞지 않는다.
DROP INDEX CONCURRENTLY IF EXISTS idx_participant_item_event_match_order;
