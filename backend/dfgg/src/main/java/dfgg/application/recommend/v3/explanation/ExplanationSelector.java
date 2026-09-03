package dfgg.application.recommend.v3.explanation;

import dfgg.application.recommend.v3.feature.ReasonGroup;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 그룹 기여도 중 사용자에게 설명할 것을 고른다.
 * <p>
 * 7개를 다 늘어놓으면 설명이 아니라 표가 되고, 상위 2개를 그냥 자르면 노이즈까지 이유로 승격된다
 * — 실제 응답에서 CONTEXT는 +0.0006 같은 값이 나온다. 그래서 "그 아이템의 양수 기여 중 몇 %인가"로 먼저 거르고,
 * 통과한 것들을 큰 순서로 최대 둘까지 쓴다.
 * <p>
 * 문턱은 모든 묶음에 같다.
 * 후보들 사이의 상대 편차로 순서를 정하는 방식도 검토했다가 버렸다.
 * 실제 응답으로 계산해보니 헤르메스의 발걸음에서 ALLY_SYNERGY(+0.249)를 COUNTER(+0.369)보다 위로 올렸다
 * — 더 작은 기여를 승격시키는 셈이다.
 * 여러 아이템이 같은 묶음을 이유로 갖는 것은 실제로 그렇기 때문이고, 아이템 간 차별화는 어느 적·어느 아군인지(evidence)가 맡는 편이 정직하다.
 */
public class ExplanationSelector {

    private static final int MAX_HIGHLIGHTS = 2;

    /** 양수 기여 총합 대비 이 비율은 넘어야 말할 가치가 있다. */
    private static final double MINIMUM_SHARE = 0.10;

    /** 이 순위 밖으로 밀린 추천에만 단서를 붙인다. 상위 추천에 붙일 말이 아니다. */
    private static final int CAVEAT_MINIMUM_RANK = 4;

    /** 단서를 달 만큼 크게 끌어내렸는가. 양수 총합 대비 비율이다. */
    private static final double CAVEAT_MINIMUM_SHARE = 0.15;

    public SelectedReasons select(Map<ReasonGroup, Double> contributionByGroup, int rank) {
        double positiveTotal = contributionByGroup.values().stream()
                .filter(value -> value > 0)
                .mapToDouble(Double::doubleValue)
                .sum();

        List<GroupWeight> positives = contributionByGroup.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(entry -> new GroupWeight(entry.getKey(), entry.getValue()))
                // 값이 같으면 선언 순서로 갈라 매 요청 같은 문장이 나오게 한다.
                .sorted(Comparator.comparingDouble(GroupWeight::value).reversed()
                        .thenComparing(GroupWeight::group))
                .toList();

        return new SelectedReasons(
                highlights(positives, positiveTotal),
                caveat(contributionByGroup, positiveTotal, rank));
    }

    /**
     * 양수 기여가 하나라도 있으면 결과도 비지 않는다. 묶음이 7개면 지분 합이 100%라 최댓값은
     * 항상 14.3% 이상이고, 그래서 {@link #MINIMUM_SHARE}가 전부를 막을 수 없다. 이 관계가
     * 깨지도록 문턱을 올리면 설명 없는 추천이 생기는데, 그건 테스트가 잡는다.
     */
    private List<GroupWeight> highlights(List<GroupWeight> positives, double positiveTotal) {
        return positives.stream()
                .filter(weight -> weight.value() / positiveTotal >= MINIMUM_SHARE)
                .limit(MAX_HIGHLIGHTS)
                .toList();
    }

    private Optional<GroupWeight> caveat(
            Map<ReasonGroup, Double> contributionByGroup, double positiveTotal, int rank) {
        if (rank < CAVEAT_MINIMUM_RANK) {
            return Optional.empty();
        }
        return contributionByGroup.entrySet().stream()
                .filter(entry -> entry.getValue() < 0)
                .min(Map.Entry.comparingByValue())
                .filter(entry -> Math.abs(entry.getValue()) >= CAVEAT_MINIMUM_SHARE * positiveTotal)
                .map(entry -> new GroupWeight(entry.getKey(), entry.getValue()));
    }
}
