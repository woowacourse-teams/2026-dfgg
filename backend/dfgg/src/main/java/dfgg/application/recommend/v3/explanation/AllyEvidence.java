package dfgg.application.recommend.v3.explanation;

import dfgg.application.recommend.v3.CandidateSource;
import dfgg.application.recommend.v3.ItemCandidate;
import dfgg.application.recommend.v3.SourceEvidence;
import dfgg.application.recommend.v3.feature.ReasonGroup;
import java.util.List;
import java.util.Map;

/**
 * 어떤 아군을 추천 이유로 댈지 정한다.
 * <p>
 * {@link CounterEvidence}와 관문 구조는 같지만 문턱의 성격이 다르다.
 * counter의 점수는 lift(평소 대비 배수)라 1을 넘는지 보면 되는데, 아군 점수는 {@code P(item | 나, 아군)}의 Wilson 하한이라 확률이다.
 * 1을 넘을 수 없으니 같은 기준을 쓸 수 없다.
 * <p>
 * 그래서 최상위 아군 점수의 절반을 문턱으로 둔다. 실측에서 불타는 향로는 징크스 0.490, 코그모 0.018로 27배 차이가 났다.
 * 랭킹에 쓴 점수는 아군별 점수의 최댓값이므로, 거기서 한참 떨어진 아군을 이유로 대면 잘못된 귀속이다.
 * 표본 크기에 따라 확률의 절대 수준이 달라지므로 고정값보다 상대 기준이 안정적이다.
 */
public final class AllyEvidence {

    /** 다섯을 다 늘어놓으면 "누구 때문인가"가 흐려진다. */
    private static final int MAXIMUM_ALLIES = 2;

    /** 최상위 점수의 이 비율에 못 미치면 이유로 대지 않는다. */
    private static final double MINIMUM_FRACTION_OF_TOP = 0.5;

    private AllyEvidence() {
    }

    public static List<Long> championIdsFor(SelectedReasons selected, ItemCandidate candidate) {
        if (!droveTheScore(selected)) {
            return List.of();
        }
        Map<Long, Double> scoreByAlly = candidate.evidenceOf(CandidateSource.ALLY_SYNERGY)
                .map(SourceEvidence::scoreByChampionId)
                .orElse(Map.of());

        double topScore = scoreByAlly.values().stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.0);
        return ChampionEvidence.topChampionIds(
                scoreByAlly, topScore * MINIMUM_FRACTION_OF_TOP, MAXIMUM_ALLIES);
    }

    private static boolean droveTheScore(SelectedReasons selected) {
        return selected.qualified().stream()
                .anyMatch(weight -> weight.group() == ReasonGroup.ALLY_SYNERGY);
    }
}
