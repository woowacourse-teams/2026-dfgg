package dfgg.application.recommend.v3.explanation;

import dfgg.application.recommend.v3.feature.ReasonGroup;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 그룹 기여도 중 근거로 쓸 만한 것을 고른다.
 * <p>
 * 규칙은 하나다 — "그 아이템의 양수 기여 중 몇 %인가"로 노이즈를 거른다.
 * 실제 응답에서 CONTEXT는 +0.0006 같은 값이 나오는데, 그런 것까지 이유로 승격시키면
 * 설명이 아니라 잡음이 된다.
 * <p>
 * <b>개수 상한은 두지 않는다.</b> 한때 상위 2개만 골랐는데, 그건 한 문장에 이유 셋을 넣으면
 * 읽기 어렵다는 <i>문장</i>의 제약이었다. 구조화된 키를 내보내는 지금은 무엇을 보여줄지
 * 클라이언트가 고르므로, 여기서 미리 자르면 근거를 잃기만 한다.
 * <p>
 * 문턱은 모든 묶음에 같다.
 * 후보들 사이의 상대 편차로 순서를 정하는 방식도 검토했다가 버렸다.
 * 실제 응답으로 계산해보니 헤르메스의 발걸음에서 ALLY_SYNERGY(+0.249)를 COUNTER(+0.369)보다 위로 올렸다
 * — 더 작은 기여를 승격시키는 셈이다.
 * 여러 아이템이 같은 묶음을 이유로 갖는 것은 실제로 그렇기 때문이고, 아이템 간 차별화는 어느 적·어느 아군인지(evidence)가 맡는 편이 정직하다.
 */
public class ExplanationSelector {

    /** 양수 기여 총합 대비 이 비율은 넘어야 말할 가치가 있다. */
    private static final double MINIMUM_SHARE = 0.10;

    /**
     * 양수 기여가 하나라도 있으면 결과도 비지 않는다. 묶음이 7개면 지분 합이 100%라 최댓값은
     * 항상 14.3% 이상이고, 그래서 {@link #MINIMUM_SHARE}가 전부를 막을 수 없다. 이 관계가
     * 깨지도록 문턱을 올리면 근거 없는 추천이 생기는데, 그건 테스트가 잡는다.
     */
    public SelectedReasons select(Map<ReasonGroup, Double> contributionByGroup) {
        double positiveTotal = contributionByGroup.values().stream()
                .filter(value -> value > 0)
                .mapToDouble(Double::doubleValue)
                .sum();

        List<GroupWeight> qualified = contributionByGroup.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(entry -> new GroupWeight(entry.getKey(), entry.getValue()))
                .filter(weight -> weight.value() / positiveTotal >= MINIMUM_SHARE)
                // 값이 같으면 선언 순서로 갈라 매 요청 같은 응답이 나오게 한다.
                .sorted(Comparator.comparingDouble(GroupWeight::value).reversed()
                        .thenComparing(GroupWeight::group))
                .toList();

        return new SelectedReasons(qualified);
    }
}
