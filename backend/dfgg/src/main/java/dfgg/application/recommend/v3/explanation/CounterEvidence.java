package dfgg.application.recommend.v3.explanation;

import dfgg.application.recommend.v3.CandidateSource;
import dfgg.application.recommend.v3.ItemCandidate;
import dfgg.application.recommend.v3.SourceEvidence;
import java.util.List;

/**
 * 어떤 적을 추천 이유로 댈지 정한다.
 * <p>
 * 두 조건을 통과해야 한다.
 *
 * <ul>
 *   <li>소속 — counter generator가 이 아이템을 후보로 냈는가. 내지 않았다면 적은 이유가 아니다.</li>
 *   <li>lift 문턱 — 그 적을 만났을 때 평소보다 확실히 더 사는가({@code > 1.2}).
 *       1.0이 "평소와 같다"라 그 근처는 이유가 되지 못한다.</li>
 * </ul>
 * <p>
 * 구매 lift를 쓴다. 치유 팀을 만나 치유 감소를 사는 것처럼 구매 자체가 적에 대한 대응이라서다.
 * 승률 lift도 실험했지만 요청에 등장하는 조합은 표본이 얇아 노이즈가 더 컸다.
 * <p>
 * base rate로 백오프한 후보는 적별 lift가 비어 있어 자연히 아무도 지목하지 않는다.
 * <p>
 * 한때 SHAP 지분 게이트가 하나 더 있었다. 걷어낸 이유는 SHAP이 "예측을 얼마나 밀었나"를
 * 답할 뿐 "이유인가"를 답하지 못하기 때문이다. 필멸자의 운명이 치유 감소가 필요한 적 조합
 * 때문에 1위로 올라왔는데(COUNTER 기여 −0.153 → +0.170) 지분이 8.68%라 설명하지 못했다.
 * 지분은 BUILD처럼 다른 묶음이 세지면 눌리므로 근거의 질과 무관하게 오르내린다.
 * <p>
 * 실질을 담당하는 것은 소속 조건이다 — 이 generator가 후보로 내놓았는가.
 * 게이트를 뺀 뒤 노출량은 8.30% → 11.41%로 늘었을 뿐 폭증하지 않았다.
 */
public final class CounterEvidence {

    /**
     * 다섯을 다 늘어놓으면 "누구 때문인가"가 흐려진다.
     */
    private static final int MAXIMUM_ENEMIES = 2;

    /**
     * lift 1.0은 "평소와 같다"는 뜻이다. 이유가 되려면 넘어야 한다.
     */
    private static final double MINIMUM_LIFT = 1.2;

    private CounterEvidence() {
    }

    public static List<Long> championIdsFor(ItemCandidate candidate) {
        return candidate.evidenceOf(CandidateSource.COUNTER)
                .map(SourceEvidence::scoreByChampionId)
                .map(liftByEnemy -> ChampionEvidence.topChampionIds(
                        liftByEnemy, MINIMUM_LIFT, MAXIMUM_ENEMIES))
                .orElse(List.of());
    }

}
