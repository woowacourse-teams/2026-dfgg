package dfgg.application.recommend.v3.generator;

/**
 * Ally-Synergy와 Counter가 공유하는 백오프 단계.
 */
public enum PairBackoffLevel {

    /** [내 챔피언 + 상대 챔피언 + 아이템] */
    TRIPLE,

    /** 조합 표본이 전부 얇을 때 챔피언 자신의 구매 분포로 후퇴 */
    BASE_RATE,
    ;
}
