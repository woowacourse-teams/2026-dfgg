-- 픽의 순서 있는 자식 행. 픽을 지우면 같이 지워진다. 스키마만 만든다.
CREATE TABLE pro_pick_spells (
    pick_id BIGINT NOT NULL REFERENCES pro_picks (id) ON DELETE CASCADE,
    -- Leaguepedia SummonerSpells 목록 순서
    slot SMALLINT NOT NULL CHECK (slot BETWEEN 0 AND 1),
    spell_id INTEGER NOT NULL REFERENCES summoner_spells (spell_id),
    PRIMARY KEY (pick_id, slot)
);

CREATE TABLE pro_pick_items (
    pick_id BIGINT NOT NULL REFERENCES pro_picks (id) ON DELETE CASCADE,
    -- 0~5 인벤토리 칸(빈칸은 행 없음), 6 장신구
    slot SMALLINT NOT NULL CHECK (slot BETWEEN 0 AND 6),
    item_id BIGINT NOT NULL REFERENCES items (item_id),
    PRIMARY KEY (pick_id, slot)
);

CREATE TABLE pro_pick_runes (
    pick_id BIGINT NOT NULL REFERENCES pro_picks (id) ON DELETE CASCADE,
    -- 0 핵심, 1~3 주 트리, 4~5 보조 트리, 6~8 능력치 파편. 트리는 0번·4번 룬의 tree_id로 구한다.
    slot SMALLINT NOT NULL CHECK (slot BETWEEN 0 AND 8),
    -- 파편도 룬 동기화가 runes에 넣으므로 FK를 건다.
    rune_id INTEGER NOT NULL REFERENCES runes (rune_id),
    PRIMARY KEY (pick_id, slot)
);
