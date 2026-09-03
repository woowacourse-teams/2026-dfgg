DELETE FROM champion_tags;
DELETE FROM champions;

INSERT INTO champions (champion_id, riot_key, name) VALUES
    (122, 'Darius', '다리우스'),
    (222, 'Jinx', '징크스'),
    (112, 'Viktor', '빅토르'),
    (777, 'NoTagChampion', '태그없는챔피언');

-- 태그는 의미상 집합이지만 @ElementCollection은 List로 매핑돼 있어 중복 행이 그대로 올라온다.
-- 로컬 백테스트 DB(dfgg_test_backtest)가 실제로 그런 상태라(덤프 2회 적재, 태그 608행)
-- 그 상태를 재현해 둔다. 운영·RDS는 정상이다.
INSERT INTO champion_tags (champion_id, tag) VALUES
    (122, 'FIGHTER'), (122, 'TANK'), (122, 'FIGHTER'), (122, 'TANK'),
    (222, 'MARKSMAN'), (222, 'MARKSMAN'),
    (112, 'MAGE');
