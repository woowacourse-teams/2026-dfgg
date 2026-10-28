-- 주문·아이템·룬 자식 테이블을 pro_picks의 배열 컬럼으로 합친다(T10 측정: 조회 쿼리 3개·갱신 문장 18개가 준다).
-- 배열 위치 = 칸 번호 + 1(PostgreSQL 배열은 1부터). 칸 수는 고정이고 빈 칸은 NULL이다.
-- 배열 원소에는 FK를 걸 수 없다. 카탈로그에 없는 값은 수집 변환이 건너뛴다.
ALTER TABLE pro_picks
    ADD COLUMN spell_ids INTEGER[],
    ADD COLUMN item_ids BIGINT[],
    ADD COLUMN rune_ids INTEGER[];

UPDATE pro_picks p
SET spell_ids = (SELECT array_agg(s.spell_id ORDER BY g.slot)
                 FROM generate_series(0, 1) g(slot)
                 LEFT JOIN pro_pick_spells s ON s.pick_id = p.id AND s.slot = g.slot),
    item_ids = (SELECT array_agg(i.item_id ORDER BY g.slot)
                FROM generate_series(0, 6) g(slot)
                LEFT JOIN pro_pick_items i ON i.pick_id = p.id AND i.slot = g.slot),
    rune_ids = (SELECT array_agg(r.rune_id ORDER BY g.slot)
                FROM generate_series(0, 8) g(slot)
                LEFT JOIN pro_pick_runes r ON r.pick_id = p.id AND r.slot = g.slot);

ALTER TABLE pro_picks
    ALTER COLUMN spell_ids SET NOT NULL,
    ALTER COLUMN item_ids SET NOT NULL,
    ALTER COLUMN rune_ids SET NOT NULL,
    -- Leaguepedia SummonerSpells 순서 2칸
    ADD CONSTRAINT ck_pro_picks_spell_slots CHECK (cardinality(spell_ids) = 2),
    -- 0~5 인벤토리, 6 장신구
    ADD CONSTRAINT ck_pro_picks_item_slots CHECK (cardinality(item_ids) = 7),
    -- 0 핵심, 1~3 주 트리, 4~5 보조 트리, 6~8 능력치 파편
    ADD CONSTRAINT ck_pro_picks_rune_slots CHECK (cardinality(rune_ids) = 9);

DROP TABLE pro_pick_spells, pro_pick_items, pro_pick_runes;
