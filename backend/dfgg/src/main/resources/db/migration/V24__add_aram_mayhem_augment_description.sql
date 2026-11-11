-- 증강 요약 설명(게임 증강 선택 카드 문구). 이름과 같은 {"ko-KR": ..., "en-US": ...} 형식이고 만든 언어만 담는다.
-- 수치를 채우지 못한 증강은 설명이 없어 NULL이다. 다음 동기화 때 패치를 통째로 교체하므로 기존 행을 따로 채우지 않는다.
ALTER TABLE aram_mayhem_augments ADD COLUMN description JSONB;
