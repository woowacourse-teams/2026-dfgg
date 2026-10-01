package dfgg.application.recommend.v3.generator;

/**
 * Self-Synergy가 어느 범위의 통계를 썼는지.
 */
public enum SelfSynergyBackoffLevel {

    /** 챔피언 + 포지션. 같은 챔피언도 포지션이 다르면 사는 게 달라 이쪽이 정확하다. */
    CHAMPION_POSITION,

    /** 포지션을 합친 챔피언 전체. 포지션 표본이 얇을 때만 쓴다. */
    CHAMPION_ROLLUP,
    ;
}
