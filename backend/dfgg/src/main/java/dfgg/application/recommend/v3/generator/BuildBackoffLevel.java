package dfgg.application.recommend.v3.generator;

/**
 * Build generator가 얼마나 조건을 완화했는지. 위로 갈수록 지금 상황에 정확하지만 표본이 얇고,
 * 아래로 갈수록 표본은 두텁지만 현재 build와의 관련성이 옅어진다.
 */
public enum BuildBackoffLevel {

    /** 구매 순서의 앞부분이 정확히 일치하는 표본에서 다음 한 칸 */
    EXACT_PREFIX,

    /** 마지막으로 산 아이템 바로 다음에 온 아이템(위치 무관) */
    LAST_ITEM,

    /** 전개를 무시하고 이 챔피언·포지션이 사는 아이템 전반 */
    CHAMPION,
    ;
}
