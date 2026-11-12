-- Component 학습 데이터 export의 패치별 경기 조회(select distinct match_id ... where patch = ?) 전용 인덱스.
--
-- 기존 인덱스가 모두 match_id로 시작해서 패치 조회는 테이블 전체를 읽었다 → 운영 2,608만 행에서 약 32.7초.
-- (patch, match_id) 순서면 그 패치 구간만 index-only scan으로 읽고, 정렬된 match_id로 중복을 바로 지운다.
-- 같은 값이 경기당 수백 번 반복돼 B-tree 중복 제거가 잘 먹힌다 → 운영 기준 185MB.
--
-- 운영에는 측정하면서 같은 정의로 먼저 만들었으므로 IF NOT EXISTS로 건너뛴다.
-- CONCURRENTLY:    구매 정규화 INSERT를 막지 않는다. 트랜잭션 안에서는 실행할 수 없는데,
-- Flyway가 이 문장을 감지해 이 파일만 트랜잭션 밖에서 실행한다 — 그래서 이 파일에 다른 문장을 섞지 않는다.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_participant_item_event_patch_match
    ON normalized_match_participant_item_events (patch, match_id);
