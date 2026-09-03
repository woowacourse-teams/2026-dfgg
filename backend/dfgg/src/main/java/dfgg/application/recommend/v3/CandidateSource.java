package dfgg.application.recommend.v3;

/**
 * 후보를 발견한 근거. 정확히 4개이며 이 목록은 늘리지 않는다 —
 * Meta·Team·Semantic은 별도 generator가 아니라 LTR feature로 다룬다.
 */
public enum CandidateSource {

    /** 현재 build와 구매 순서를 볼 때 다음에 무엇을 사는가 */
    BUILD,

    /** 이 아이템이 내 챔피언과 얼마나 잘 맞는가 */
    SELF_SYNERGY,

    /** 우리 팀 조합 때문에 어떤 아이템이 좋은가 */
    ALLY_SYNERGY,

    /** 현재 적 조합 때문에 어떤 아이템의 가치가 오르는가 */
    COUNTER,
    ;
}
