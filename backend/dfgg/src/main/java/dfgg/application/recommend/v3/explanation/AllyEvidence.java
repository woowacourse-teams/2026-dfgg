package dfgg.application.recommend.v3.explanation;

import dfgg.application.recommend.v3.CandidateSource;
import dfgg.application.recommend.v3.ItemCandidate;
import dfgg.application.recommend.v3.SourceEvidence;
import java.util.List;

/**
 * 어떤 아군을 추천 이유로 댈지 정한다. {@link CounterEvidence}와 같은 기준을 쓴다 — lift {@code > 1}.
 * <p>
 * 한때는 "최상위 아군 점수의 절반"이라는 상대 문턱이었다. 아군 점수가
 * {@code P(item | 나, 아군)}의 Wilson 하한, 즉 확률이라 1을 넘을 수 없었기 때문이다.
 * <p>
 * 그런데 상대 기준은 <b>아군 넷의 점수가 고만고만하면 아무나 통과</b>시킨다.
 * 실측에서 원딜에게 무한의 대검이 추천될 때 이렐리아·요네·피즈가 이유로 붙었다 —
 * 원딜이면 거의 다 사는 아이템이라 특정 아군으로 설명될 이유가 없는데도 그랬다.
 * 점수를 lift로 바꾸면 "평소보다 더 산다"가 되어 그런 아이템이 자연히 걸러진다.
 * <p>
 * SHAP 지분 게이트는 걷어냈다({@link CounterEvidence} 참고). ally는 특히 손해를 봤는데,
 * 게이트를 빼자 노출량이 10.44% → 20.92%로 두 배가 됐다 — 절반이 지분에 막혀 있었다.
 */
public final class AllyEvidence {

    /** 넷을 다 늘어놓으면 "누구 때문인가"가 흐려진다. */
    private static final int MAXIMUM_ALLIES = 2;

    /**
     * 이 아군과 함께일 때 뚜렷이 더 사야 이유가 된다.
     */
    private static final double MINIMUM_LIFT = 1.2;

    private AllyEvidence() {
    }

    public static List<Long> championIdsFor(ItemCandidate candidate) {
        return candidate.evidenceOf(CandidateSource.ALLY_SYNERGY)
                .map(SourceEvidence::scoreByChampionId)
                .map(liftByAlly -> ChampionEvidence.topChampionIds(
                        liftByAlly, MINIMUM_LIFT, MAXIMUM_ALLIES))
                .orElse(List.of());
    }

}
