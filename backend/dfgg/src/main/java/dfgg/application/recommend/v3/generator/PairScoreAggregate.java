package dfgg.application.recommend.v3.generator;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 아이템 하나에 대한 <b>상대 챔피언별</b> 점수와 그 집계.
 * Ally-Synergy(아군별 궁합)와 Counter(적별 lift)가 같은 구조를 쓴다.
 *
 * <p>5명을 하나의 window로 뭉치지 않고 개별 관계를 유지하는 것이 두 generator의 핵심이라,
 * 집계값만 남기고 개별 점수를 버리면 그 구조가 무너진다. 개별 점수와 집계값을 모두 들고 있다가
 * 전부 LTR feature로 넘긴다 — 어느 쪽이 유효한지는 모델이 판단한다.
 *
 * <p>{@code mean}은 <b>관측된 상대 수</b>로 나눈다. 관측되지 않은 상대를 0으로 채워 나누면
 * "데이터가 없다"가 "궁합이 나쁘다"로 바뀌어버린다. 둘은 다른 정보다.
 */
public record PairScoreAggregate(Map<Long, Double> scoreByOtherChampionId) {

    public PairScoreAggregate {
        scoreByOtherChampionId = Map.copyOf(scoreByOtherChampionId);
    }

    public static PairScoreAggregate of(Map<Long, Double> scoreByOtherChampionId) {
        return new PairScoreAggregate(scoreByOtherChampionId);
    }

    /** 관측되지 않은 상대는 0. 존재 여부는 {@link #scoreByOtherChampionId()}로 구분한다. */
    public double scoreOf(long otherChampionId) {
        return scoreByOtherChampionId.getOrDefault(otherChampionId, 0.0);
    }

    public double max() {
        return descendingScores().stream().findFirst().orElse(0.0);
    }

    public double sum() {
        return scoreByOtherChampionId.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    public double mean() {
        if (scoreByOtherChampionId.isEmpty()) {
            return 0.0;
        }
        return sum() / scoreByOtherChampionId.size();
    }

    public double top1() {
        return max();
    }

    /** 두 번째로 높은 상대 점수. 상대가 하나뿐이면 0. */
    public double top2() {
        List<Double> descending = descendingScores();
        if (descending.size() < 2) {
            return 0.0;
        }
        return descending.get(1);
    }

    private List<Double> descendingScores() {
        return scoreByOtherChampionId.values().stream()
                .sorted(Comparator.reverseOrder())
                .toList();
    }
}
