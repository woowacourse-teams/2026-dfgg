package dfgg.application.recommend.v3.explanation;

import dfgg.application.recommend.v3.CandidateSource;
import dfgg.application.recommend.v3.ItemCandidate;
import dfgg.application.recommend.v3.SourceEvidence;
import dfgg.application.recommend.v3.feature.ReasonGroup;
import java.util.List;

/**
 * 어떤 적을 추천 이유로 댈지 정한다.
 * <p>
 * 두 관문을 통과해야 한다.
 *
 * <ul>
 *   <li>SHAP 게이트 — counter 묶음이 실제로 이 아이템의 점수를 올렸는가.
 *       없으면 모델이 무시한 근거까지 이유가 된다.</li>
 *   <li>lift 문턱 — 그 적을 만났을 때 평소보다 더 사는가.
 *       lift가 1 이하면 "평소와 같거나 덜 산다"는 뜻이라 이유로 댈 수 없다.</li>
 * </ul>
 * <p>
 * base rate로 백오프한 후보는 적별 lift가 비어 있어 자연히 아무도 지목하지 않는다.
 */
public final class CounterEvidence {

    /**
     * 다섯을 다 늘어놓으면 "누구 때문인가"가 흐려진다.
     */
    private static final int MAXIMUM_ENEMIES = 2;

    /**
     * lift 1.0은 "평소와 같다"는 뜻이다. 이유가 되려면 넘어야 한다.
     */
    private static final double NEUTRAL_LIFT = 1.0;

    private CounterEvidence() {
    }

    public static List<Long> championIdsFor(SelectedReasons selected, ItemCandidate candidate) {
        if (!droveTheScore(selected)) {
            return List.of();
        }
        return candidate.evidenceOf(CandidateSource.COUNTER)
                .map(SourceEvidence::scoreByChampionId)
                .map(liftByEnemy -> ChampionEvidence.topChampionIds(
                        liftByEnemy, NEUTRAL_LIFT, MAXIMUM_ENEMIES))
                .orElse(List.of());
    }

    private static boolean droveTheScore(SelectedReasons selected) {
        return selected.highlights().stream()
                .anyMatch(weight -> weight.group() == ReasonGroup.COUNTER);
    }
}
