package dfgg.application.recommend.v3.ranker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * LightGBM이 내보낸 트리를 그대로 순회한다.
 *
 * <p>자식 인덱스 규약: 0 이상이면 분기 노드 인덱스, 음수면 잎이며 {@code -index - 1}이 잎 번호다.
 * Python 쪽에서 이 규약으로 순회한 예측이 LightGBM 원본과 1e-9 이내로 일치함을 이미 확인했다
 * ({@code ml/tests/test_flatten_equivalence.py}).
 */
class GradientBoostedTreesTest {

    /** f0 <= 0.5 면 잎0(1.0), 아니면 잎1(2.0). NaN은 왼쪽. */
    private DecisionTree simpleTree(boolean defaultLeft) {
        return new DecisionTree(
                new int[]{0}, new double[]{0.5}, new boolean[]{defaultLeft},
                new int[]{-1}, new int[]{-2}, new double[]{1.0, 2.0},
                new double[]{10.0}, new double[]{6.0, 4.0});
    }

    @Test
    @DisplayName("분기가 없는 트리는 잎 값을 그대로 낸다 — 학습이 일찍 수렴하면 실제로 나온다")
    void predict_WhenTreeHasSingleLeaf_ReturnsThatLeafValue() {
        DecisionTree tree = new DecisionTree(
                new int[]{}, new double[]{}, new boolean[]{},
                new int[]{}, new int[]{}, new double[]{0.42},
                new double[]{}, new double[]{10.0});

        assertThat(new GradientBoostedTrees(List.of(tree)).predict(new double[]{1.0}))
                .isEqualTo(0.42);
    }

    @Test
    @DisplayName("임계값 이하면 왼쪽으로 간다")
    void predict_WhenValueIsAtOrBelowThreshold_GoesLeft() {
        GradientBoostedTrees trees = new GradientBoostedTrees(List.of(simpleTree(true)));

        assertThat(trees.predict(new double[]{0.3})).isEqualTo(1.0);
    }

    @Test
    @DisplayName("임계값과 정확히 같으면 왼쪽이다 — LightGBM의 `<=` 규약")
    void predict_WhenValueEqualsThreshold_GoesLeft() {
        GradientBoostedTrees trees = new GradientBoostedTrees(List.of(simpleTree(true)));

        assertThat(trees.predict(new double[]{0.5})).isEqualTo(1.0);
    }

    @Test
    @DisplayName("임계값을 넘으면 오른쪽으로 간다")
    void predict_WhenValueExceedsThreshold_GoesRight() {
        GradientBoostedTrees trees = new GradientBoostedTrees(List.of(simpleTree(true)));

        assertThat(trees.predict(new double[]{0.7})).isEqualTo(2.0);
    }

    @Test
    @DisplayName("결측은 임계값이 아니라 default_left 방향으로 간다 — 결측을 0으로 취급하면 안 된다")
    void predict_WhenValueIsNaN_FollowsDefaultLeft() {
        GradientBoostedTrees goesLeft = new GradientBoostedTrees(List.of(simpleTree(true)));
        GradientBoostedTrees goesRight = new GradientBoostedTrees(List.of(simpleTree(false)));

        assertThat(goesLeft.predict(new double[]{Double.NaN})).isEqualTo(1.0);
        assertThat(goesRight.predict(new double[]{Double.NaN})).isEqualTo(2.0);
    }

    @Test
    @DisplayName("여러 트리의 잎 값을 더한다 — 부스팅은 트리 합이다")
    void predict_WhenMultipleTrees_SumsLeafValues() {
        GradientBoostedTrees trees = new GradientBoostedTrees(
                List.of(simpleTree(true), simpleTree(true)));

        assertThat(trees.predict(new double[]{0.3})).isCloseTo(2.0, within(1e-12));
    }

    @Test
    @DisplayName("깊은 트리에서도 규약대로 잎에 도달한다")
    void predict_WhenTreeIsDeep_ReachesCorrectLeaf() {
        // 노드0: f0<=0.5 → 노드1 / 잎2(3.0)
        // 노드1: f1<=0.5 → 잎0(1.0) / 잎1(2.0)
        DecisionTree tree = new DecisionTree(
                new int[]{0, 1}, new double[]{0.5, 0.5}, new boolean[]{true, true},
                new int[]{1, -1}, new int[]{-3, -2}, new double[]{1.0, 2.0, 3.0},
                new double[]{10.0, 6.0}, new double[]{4.0, 2.0, 4.0});
        GradientBoostedTrees trees = new GradientBoostedTrees(List.of(tree));

        assertThat(trees.predict(new double[]{0.1, 0.1})).isEqualTo(1.0);
        assertThat(trees.predict(new double[]{0.1, 0.9})).isEqualTo(2.0);
        assertThat(trees.predict(new double[]{0.9, 0.1})).isEqualTo(3.0);
    }

    @Test
    @DisplayName("배열 길이가 어긋난 트리는 거부한다 — 형식이 깨진 채 예측하면 조용히 틀린다")
    void construct_WhenNodeArraysHaveDifferentLengths_ThrowsException() {
        assertThatThrownBy(() -> new DecisionTree(
                new int[]{0, 1}, new double[]{0.5}, new boolean[]{true, true},
                new int[]{-1, -2}, new int[]{-1, -2}, new double[]{1.0},
                new double[]{10.0, 6.0}, new double[]{6.0, 4.0}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("feature 벡터가 스키마보다 짧으면 거부한다")
    void predict_WhenFeatureVectorIsTooShort_ThrowsException() {
        GradientBoostedTrees trees = new GradientBoostedTrees(List.of(
                new DecisionTree(new int[]{5}, new double[]{0.5}, new boolean[]{true},
                        new int[]{-1}, new int[]{-2}, new double[]{1.0, 2.0},
                        new double[]{10.0}, new double[]{6.0, 4.0})));

        assertThatThrownBy(() -> trees.predict(new double[]{0.1}))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
