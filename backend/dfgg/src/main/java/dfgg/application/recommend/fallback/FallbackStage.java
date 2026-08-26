package dfgg.application.recommend.fallback;

/**
 * 추천 폴백 체인의 각 단계. 위로 갈수록 정확하지만 데이터를 많이 요구하고,
 * 아래로 갈수록 부정확하지만 거의 항상 답이 있다
 */
public enum FallbackStage {

    /** 80%(PrefixSpan+Wilson) + 20%(카운터 maxSim) + late-interaction 재정렬 */
    PRIMARY,

    /** patch/tier 조건을 완화해 재조회 */
    RELAXED_SCOPE,

    /** k-NN 기반 유사 상황 검색 */
    SIMILAR_SITUATION,

    /** 기존 composition_stats boolean 버킷 매칭 */
    COMPOSITION_STATS,

    /** 챔피언+포지션 최다빈도 빌드 (최종 안전망) */
    MOST_FREQUENT_BUILD,
    ;
}
