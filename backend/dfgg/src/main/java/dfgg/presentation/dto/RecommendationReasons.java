package dfgg.presentation.dto;

import dfgg.application.recommend.v3.feature.FeatureName;
import dfgg.application.recommend.v3.feature.ReasonGroup;
import dfgg.application.recommend.v3.ranker.FeatureContributions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 이 아이템이 왜 위로 올라왔는지. feature별 SHAP 기여도를 출처별 묶음으로 접은 값이다.
 * <p>
 * 이전에는 generator가 준 점수와 순위를 그대로 실었는데, 그건 "발견된 이유"이지 "순위가 오른 이유"가 아니었다.
 * <p>
 * SHAP은 가산적이라 그룹 기여도는 구성원 기여도의 합이고,
 * {@code baseValue + sum(contributions)}는 여전히 모델 점수와 같다.
 */
public record RecommendationReasons(
        List<GroupContribution> contributions,
        double baseValue
) {

    public static RecommendationReasons of(FeatureContributions featureContributions) {
        Map<ReasonGroup, Double> totals = new EnumMap<>(ReasonGroup.class);
        for (ReasonGroup group : ReasonGroup.values()) {
            totals.put(group, 0.0);
        }
        for (FeatureName feature : FeatureName.values()) {
            totals.merge(ReasonGroup.of(feature), featureContributions.values()[feature.index()], Double::sum);
        }

        List<GroupContribution> ordered = new ArrayList<>();
        totals.entrySet().stream()
                // 기여가 큰 묶음이 앞에 온다. 같으면 선언 순서로 갈라 매 요청 순서를 고정한다.
                .sorted(Comparator.<Map.Entry<ReasonGroup, Double>>comparingDouble(Map.Entry::getValue)
                        .reversed()
                        .thenComparing(Map.Entry::getKey))
                .forEach(entry -> ordered.add(new GroupContribution(entry.getKey().name(), entry.getValue())));

        return new RecommendationReasons(List.copyOf(ordered), featureContributions.baseValue());
    }
}
