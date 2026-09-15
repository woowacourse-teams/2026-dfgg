-- ally 근거를 응답까지 확인하려면 세 픽스처를 겹쳐야 한다.
--
--   v3-recommendation-test-data.sql   챔피언·아이템 메타데이터
--   ally-synergy-test-data.sql        잔나 + 아군별 삼중항 (참가자만 넣는다)
--   이 파일                            앞의 둘에 없는 챔피언·아이템을 채운다
--
-- v3 픽스처에는 잔나·코그모가 없고 서포터 아이템도 없다.
INSERT INTO champions (champion_id, riot_key, name) VALUES
    (40, 'Janna', '잔나'),
    (96, 'KogMaw', '코그모')
ON CONFLICT (champion_id) DO NOTHING;

INSERT INTO items (item_id, name, tags) VALUES
    (3504, '불타는 향로', '["SpellDamage","AbilityHaste"]'::jsonb),
    (6617, '월석 재생기', '["SpellDamage","AbilityHaste"]'::jsonb),
    (3222, '미카엘의 도가니', '["Tenacity","AbilityHaste"]'::jsonb),
    (3190, '강철의 솔라리 펜던트', '["Armor","SpellBlock"]'::jsonb)
ON CONFLICT (item_id) DO NOTHING;
