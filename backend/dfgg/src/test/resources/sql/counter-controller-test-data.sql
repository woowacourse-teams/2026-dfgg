-- 내 포지션 MID, 상대 공개 픽 아리(103)·다리우스(122).
--
-- 라인 분포(16.10): 아리 MID 9 · TOP 1 / 다리우스 TOP 4   ← 16.9 행은 기준 패치가 아니라 무시
-- 카운터: (아리, MID) 1위 말자하(90) · 2위 리 신(64) · 3위 애니(1)
--         (다리우스, TOP) 1위 가렌(86)   ← 내 포지션(MID)이 아니라 다리우스 카운터는 빈 목록
DELETE FROM champion_counters;
DELETE FROM champion_position_stats;
DELETE FROM champion_tags;
DELETE FROM champions;

INSERT INTO champions (champion_id, riot_key, name) VALUES
    (103, 'Ahri', jsonb_build_object('ko-KR', '아리', 'en-US', 'Ahri')),
    (122, 'Darius', jsonb_build_object('ko-KR', '다리우스', 'en-US', 'Darius')),
    (90, 'Malzahar', jsonb_build_object('ko-KR', '말자하', 'en-US', 'Malzahar')),
    (64, 'LeeSin', jsonb_build_object('ko-KR', '리 신', 'en-US', 'Lee Sin')),
    (1, 'Annie', jsonb_build_object('ko-KR', '애니', 'en-US', 'Annie')),
    (86, 'Garen', jsonb_build_object('ko-KR', '가렌', 'en-US', 'Garen')),
    (157, 'Yasuo', jsonb_build_object('ko-KR', '야스오', 'en-US', 'Yasuo')),
    -- 밴 개수 검증용. 등록된 챔피언이어야 미등록 검사가 아니라 개수 검증이 400을 낸다.
    (201, 'Ban201', jsonb_build_object('ko-KR', '밴201', 'en-US', 'Ban201')),
    (202, 'Ban202', jsonb_build_object('ko-KR', '밴202', 'en-US', 'Ban202')),
    (203, 'Ban203', jsonb_build_object('ko-KR', '밴203', 'en-US', 'Ban203')),
    (204, 'Ban204', jsonb_build_object('ko-KR', '밴204', 'en-US', 'Ban204')),
    (205, 'Ban205', jsonb_build_object('ko-KR', '밴205', 'en-US', 'Ban205')),
    (206, 'Ban206', jsonb_build_object('ko-KR', '밴206', 'en-US', 'Ban206')),
    (207, 'Ban207', jsonb_build_object('ko-KR', '밴207', 'en-US', 'Ban207')),
    (208, 'Ban208', jsonb_build_object('ko-KR', '밴208', 'en-US', 'Ban208')),
    (209, 'Ban209', jsonb_build_object('ko-KR', '밴209', 'en-US', 'Ban209')),
    (210, 'Ban210', jsonb_build_object('ko-KR', '밴210', 'en-US', 'Ban210')),
    (211, 'Ban211', jsonb_build_object('ko-KR', '밴211', 'en-US', 'Ban211'));

INSERT INTO champion_position_stats (patch, champion_id, position, games) VALUES
    ('16.9', 103, 'MID', 100),
    ('16.10', 103, 'MID', 9),
    ('16.10', 103, 'TOP', 1),
    ('16.10', 122, 'TOP', 4);

INSERT INTO champion_counters (enemy_champion_id, position, counter_champion_id, rank) VALUES
    (103, 'MID', 90, 1),
    (103, 'MID', 64, 2),
    (103, 'MID', 1, 3),
    (122, 'TOP', 86, 1);
