package dfgg.application.recommend.v3.generator;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 아이템 하나에 대한 <b>아군별</b> 궁합 점수와 그 집계.
 *
 * <p>아군 5명을 하나의 window로 뭉치지 않고 개별 관계를 유지하는 것이 Ally-Synergy의 핵심이라,
 * 집계값만 남기고 개별 점수를 버리면 그 구조가 무너진다. 개별 점수와 집계값을 모두 들고 있다가
 * 전부 LTR feature로 넘긴다 — 어느 쪽이 유효한지는 모델이 판단한다.
 *
 * <p>{@code mean}은 <b>관측된 아군 수</b>로 나눈다. 관측되지 않은 아군을 0으로 채워 나누면
 * "데이터가 없다"가 "궁합이 나쁘다"로 바뀌어버린다. 둘은 다른 정보다.
 */
public record AllyScoreAggregate(Map<Long, Double> scoreByAllyChampionId) {

    public AllyScoreAggregate {
        scoreByAllyChampionId = Map.copyOf(scoreByAllyChampionId);
    }

    public static AllyScoreAggregate of(Map<Long, Double> scoreByAllyChampionId) {
        return new AllyScoreAggregate(scoreByAllyChampionId);
    }

    /** 관측되지 않은 아군은 0. 존재 여부는 {@link #scoreByAllyChampionId()}로 구분한다. */
    public double scoreOf(long allyChampionId) {
        return scoreByAllyChampionId.getOrDefault(allyChampionId, 0.0);
    }

    public double max() {
        return descendingScores().stream().findFirst().orElse(0.0);
    }

    public double sum() {
        return scoreByAllyChampionId.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    public double mean() {
        if (scoreByAllyChampionId.isEmpty()) {
            return 0.0;
        }
        return sum() / scoreByAllyChampionId.size();
    }

    public double top1() {
        return max();
    }

    /** 두 번째로 높은 아군 점수. 아군이 하나뿐이면 0. */
    public double top2() {
        List<Double> descending = descendingScores();
        if (descending.size() < 2) {
            return 0.0;
        }
        return descending.get(1);
    }

    private List<Double> descendingScores() {
        return scoreByAllyChampionId.values().stream()
                .sorted(Comparator.reverseOrder())
                .toList();
    }
}
