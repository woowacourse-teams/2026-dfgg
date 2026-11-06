-- 대회 픽 응답은 룬 칸의 룬으로 주 트리·보조 트리를 구한다. 룬마다 속한 계열(STYLE)의 id를 둔다.
-- 계열 행은 자기 id, 계열 안의 룬은 그 계열 id, 능력치 파편은 계열이 없어 비어 있다.
-- 기존 행은 다음 룬 동기화(POST /admin/runes)에서 채워진다. 이 마이그레이션은 스키마만 바꾼다.
ALTER TABLE runes ADD COLUMN tree_id INTEGER;
