package dfgg.domain.itemstats;

/**
 * 내 챔피언과 상대 챔피언의 관계. Ally-Synergy와 Counter가 같은 통계 구조를 공유하되
 * 이 값으로 갈린다 — 집계 로직과 lift 계산식이 동일해서 테이블을 둘로 쪼개면 같은 코드를
 * 두 벌 유지하게 된다.
 */
public enum PairRelation {

    /** 같은 팀 */
    ALLY,

    /** 상대 팀 */
    ENEMY,
    ;
}
