-- 룬 이름 카탈로그. 추천 응답은 Data Dragon을 부르지 않고 이 테이블과 S3 이미지만 쓴다.
-- 이미지는 S3의 dfgg/images/{패치}/runes/{rune_id}.png에 따로 저장한다.
CREATE TABLE runes (
    rune_id INTEGER PRIMARY KEY CHECK (rune_id > 0),
    -- 계열(STYLE), 계열 안의 룬(PERK), 능력치 파편(STAT_SHARD)을 한 테이블에 둔다.
    kind VARCHAR(16) NOT NULL CHECK (kind IN ('STYLE', 'PERK', 'STAT_SHARD')),
    -- 챔피언·아이템과 같은 {"ko-KR": ..., "en-US": ...} 형식
    name JSONB NOT NULL
);
