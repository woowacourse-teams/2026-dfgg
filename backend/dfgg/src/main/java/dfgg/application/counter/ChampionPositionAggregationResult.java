package dfgg.application.counter;

/**
 * 라인 분포 집계 결과. 집계할 패치가 없으면 {@code patch}는 null이다.
 */
public record ChampionPositionAggregationResult(String patch, int savedRows) {

    public static ChampionPositionAggregationResult empty() {
        return new ChampionPositionAggregationResult(null, 0);
    }
}
