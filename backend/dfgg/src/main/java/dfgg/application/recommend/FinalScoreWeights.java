package dfgg.application.recommend;

/**
 * finalScore 4개 항의 가중치(w1~w4). 코드에 고정값으로 박혀있지 않고 호출자가 넘긴다
 * (백테스트로 튜닝 예정, {@code tasks/plan.md} "교체 전 검증" 절 참고).
 */
public record FinalScoreWeights(
        double wilsonWeight,
        double myChampionWeight,
        double allyWeight,
        double enemyWeight
) {

}
