package dfgg.application.recommend.v3.ranker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 각 feature가 이 아이템의 점수를 얼마나 올리고 내렸는지 계산한다 (TreeSHAP, path-dependent).
 * <p>
 * generator가 준 점수와는 다른 값이다.
 * {@code counter_score}는 단독 NDCG@1이 0.001인데도 값 자체는 클 수 있음을 확인했다.
 * "카운터 점수가 높다"와 "카운터 때문에 순위가 올랐다"는 다른 말이고, 사용자에게 보여줄 이유는 뒤쪽이다.
 */
class TreeShapCalculatorTest {

    /** f0 <= 0.5 → 0.0, 아니면 10.0. 표본이 반반이라 기대값은 5.0이다. */
    private DecisionTree evenSplitOnFirstFeature() {
        return new DecisionTree(
                new int[]{0}, new double[]{0.5}, new boolean[]{true},
                new int[]{-1}, new int[]{-2}, new double[]{0.0, 10.0},
                new double[]{10.0}, new double[]{5.0, 5.0});
    }

    private TreeShapCalculator calculatorOf(DecisionTree... trees) {
        return new TreeShapCalculator(new GradientBoostedTrees(List.of(trees)), 3);
    }

    @Test
    @DisplayName("기여도 합에 기준값을 더하면 예측값이 된다 — SHAP의 정의이자 '이유'라 부를 근거")
    void contributions_PlusBaseValue_EqualThePrediction() {
        GradientBoostedTrees model = new GradientBoostedTrees(List.of(evenSplitOnFirstFeature()));
        TreeShapCalculator calculator = new TreeShapCalculator(model, 3);
        double[] features = {0.9, 0.1, 0.2};

        FeatureContributions contributions = calculator.contributions(features);

        double total = contributions.baseValue();
        for (double value : contributions.values()) {
            total += value;
        }
        assertThat(total).isCloseTo(model.predict(features), within(1e-9));
    }

    @Test
    @DisplayName("기준값은 잎 값을 cover로 가중평균한 값이다 — 입력과 무관하다")
    void baseValue_IsTheCoverWeightedMeanOfLeaves_AndDoesNotDependOnInput() {
        TreeShapCalculator calculator = calculatorOf(evenSplitOnFirstFeature());

        double base = calculator.contributions(new double[]{0.9, 0.0, 0.0}).baseValue();

        assertThat(base).isCloseTo(5.0, within(1e-9));
        assertThat(calculator.contributions(new double[]{0.1, 0.0, 0.0}).baseValue())
                .isCloseTo(base, within(1e-12));
    }

    @Test
    @DisplayName("분기에 쓰인 feature가 기여를 가져간다")
    void contributions_AttributeTheSwingToTheSplitFeature() {
        TreeShapCalculator calculator = calculatorOf(evenSplitOnFirstFeature());

        // 기대값 5.0 → 예측 10.0. 그 차이 전부가 f0의 몫이다.
        assertThat(calculator.contributions(new double[]{0.9, 0.0, 0.0}).values()[0])
                .isCloseTo(5.0, within(1e-9));
        assertThat(calculator.contributions(new double[]{0.1, 0.0, 0.0}).values()[0])
                .isCloseTo(-5.0, within(1e-9));
    }

    @Test
    @DisplayName("모델이 쓰지 않는 feature의 기여는 정확히 0이다 — 없는 이유를 지어내지 않는다")
    void contributions_AreZeroForFeaturesTheModelNeverSplitsOn() {
        TreeShapCalculator calculator = calculatorOf(evenSplitOnFirstFeature());

        double[] values = calculator.contributions(new double[]{0.9, 0.4, 0.7}).values();

        assertThat(values[1]).isZero();
        assertThat(values[2]).isZero();
    }

    @Test
    @DisplayName("잎 하나뿐인 트리는 기여가 없고 기준값만 남는다")
    void contributions_WhenTreeIsASingleLeaf_AreAllZero() {
        DecisionTree singleLeaf = new DecisionTree(
                new int[]{}, new double[]{}, new boolean[]{},
                new int[]{}, new int[]{}, new double[]{3.0},
                new double[]{}, new double[]{10.0});

        FeatureContributions contributions = calculatorOf(singleLeaf)
                .contributions(new double[]{0.1, 0.2, 0.3});

        assertThat(contributions.values()).containsOnly(0.0);
        assertThat(contributions.baseValue()).isCloseTo(3.0, within(1e-12));
    }

    @Test
    @DisplayName("트리가 여러 개면 기여도가 더해진다")
    void contributions_AreSummedAcrossTrees() {
        TreeShapCalculator calculator = calculatorOf(
                evenSplitOnFirstFeature(), evenSplitOnFirstFeature());

        assertThat(calculator.contributions(new double[]{0.9, 0.0, 0.0}).values()[0])
                .isCloseTo(10.0, within(1e-9));
    }

    @Test
    @DisplayName("결측도 default_left 경로로 계산된다 — 결측이라고 기여가 사라지지 않는다")
    void contributions_HandleMissingValuesViaDefaultLeft() {
        GradientBoostedTrees model = new GradientBoostedTrees(List.of(evenSplitOnFirstFeature()));
        TreeShapCalculator calculator = new TreeShapCalculator(model, 3);
        double[] features = {Double.NaN, 0.0, 0.0};

        FeatureContributions contributions = calculator.contributions(features);

        double total = contributions.baseValue() + contributions.values()[0];
        assertThat(total).isCloseTo(model.predict(features), within(1e-9));
    }

    @Test
    @DisplayName("불균형한 cover는 기준값을 그쪽으로 당긴다 — cover를 무시하면 여기서 틀어진다")
    void contributions_RespectCoverImbalance() {
        // 왼쪽(0.0)에 9할, 오른쪽(10.0)에 1할 → 기대값 1.0
        DecisionTree skewed = new DecisionTree(
                new int[]{0}, new double[]{0.5}, new boolean[]{true},
                new int[]{-1}, new int[]{-2}, new double[]{0.0, 10.0},
                new double[]{10.0}, new double[]{9.0, 1.0});

        assertThat(calculatorOf(skewed).contributions(new double[]{0.9, 0.0, 0.0}).baseValue())
                .isCloseTo(1.0, within(1e-9));
    }
}
