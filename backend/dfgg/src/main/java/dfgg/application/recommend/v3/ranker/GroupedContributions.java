package dfgg.application.recommend.v3.ranker;

import dfgg.application.recommend.v3.feature.FeatureName;
import dfgg.application.recommend.v3.feature.ReasonGroup;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * feature별 SHAP 기여도를 {@link ReasonGroup} 묶음으로 접은 값. 서버 디버그 로그용이다.
 * <p>
 * SHAP은 가산적이라 묶음 기여도는 구성원 기여도의 합이고, {@code baseValue + 합}은 여전히 모델 점수다.
 * <p>
 * 응답에는 싣지 않는다. SHAP은 "예측을 얼마나 밀었나"를 답할 뿐 "이유인가"를 답하지 못한다 —
 * 근거가 없어 결측인 counter feature가 오히려 양의 기여로 잡힌 일이 있었다.
 * 부호가 뒤집히는 경위를 추적하는 데는 여전히 쓸모가 있어 로그로 남긴다.
 */
public record GroupedContributions(List<GroupContribution> ordered, double baseValue) {

    public GroupedContributions {
        ordered = List.copyOf(ordered);
    }

    /** 한 묶음이 점수를 얼마나 올리고(양수) 내렸는지(음수). 모델의 raw margin 단위라 확률이 아니다. */
    public record GroupContribution(ReasonGroup group, double value) {
    }

    public static GroupedContributions of(FeatureContributions featureContributions) {
        Map<ReasonGroup, Double> totals = new EnumMap<>(ReasonGroup.class);
        for (ReasonGroup group : ReasonGroup.values()) {
            totals.put(group, 0.0);
        }
        for (FeatureName feature : FeatureName.values()) {
            totals.merge(ReasonGroup.of(feature), featureContributions.values()[feature.index()], Double::sum);
        }
        // 기여가 큰 묶음이 앞에 온다. 같으면 선언 순서로 갈라 같은 입력의 로그가 매번 같게 한다.
        List<GroupContribution> ordered = totals.entrySet().stream()
                .sorted(Comparator.<Map.Entry<ReasonGroup, Double>>comparingDouble(Map.Entry::getValue)
                        .reversed()
                        .thenComparing(Map.Entry::getKey))
                .map(entry -> new GroupContribution(entry.getKey(), entry.getValue()))
                .toList();
        return new GroupedContributions(ordered, featureContributions.baseValue());
    }

    public double valueOf(ReasonGroup group) {
        return ordered.stream()
                .filter(contribution -> contribution.group() == group)
                .findFirst()
                .orElseThrow()
                .value();
    }

    /** 로그 한 줄에 싣는 형태. 예: {@code COUNTER=+0.170, BUILD=+0.052, ...} */
    public String describe() {
        return ordered.stream()
                .map(contribution -> String.format(Locale.ROOT, "%s=%+.3f", contribution.group(), contribution.value()))
                .collect(Collectors.joining(", "));
    }
}
