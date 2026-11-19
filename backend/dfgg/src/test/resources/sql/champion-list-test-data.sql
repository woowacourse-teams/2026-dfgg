-- GET /api/champions 계약 확인용. 영문 키 순서(A·G·M)·id 순서(62·86·266)·한글 순서(가·손·아)가 모두 다르다.
DELETE FROM champion_tags;
DELETE FROM champions;

INSERT INTO champions (champion_id, riot_key, name) VALUES
    (266, 'Aatrox', jsonb_build_object('ko-KR', '아트록스', 'en-US', 'Aatrox')),
    (86, 'Garen', jsonb_build_object('ko-KR', '가렌', 'en-US', 'Garen')),
    (62, 'MonkeyKing', jsonb_build_object('ko-KR', '손오공', 'en-US', 'Wukong'));
