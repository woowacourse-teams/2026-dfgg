-- 대회 픽의 소환사 주문 카탈로그. Data Dragon summoner.json에서 적재한다.
CREATE TABLE summoner_spells (
    -- Data Dragon key (예: 4 = 점멸)
    spell_id INTEGER PRIMARY KEY,
    -- Data Dragon id (예: SummonerFlash)
    riot_key VARCHAR(64) NOT NULL UNIQUE,
    name JSONB NOT NULL,
    icon_key TEXT NOT NULL
);
