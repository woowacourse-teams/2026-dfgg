package dfgg.application.recommend.v3.ranker;

/**
 * LightGBM 트리 하나를 평탄 배열로 담는다.
 *
 * <p>{@code left}/{@code right}의 값이 0 이상이면 분기 노드의 인덱스, 음수면 잎이며 잎 번호는
 * {@code -index - 1}이다. Python export가 쓰는 규약과 동일하며, 그 규약이 LightGBM 원본 예측과
 * 일치함은 {@code ml/tests/test_flatten_equivalence.py}에서 확인했다.
 */
public record DecisionTree(
        int[] splitFeature,
        double[] threshold,
        boolean[] defaultLeft,
        int[] left,
        int[] right,
        double[] leafValue,
        double[] nodeCover,
        double[] leafCover
) {

    public DecisionTree {
        int nodeCount = splitFeature.length;
        if (threshold.length != nodeCount
                || defaultLeft.length != nodeCount
                || left.length != nodeCount
                || right.length != nodeCount) {
            throw new IllegalArgumentException(
                    "트리 노드 배열 길이가 일치하지 않습니다: splitFeature=%d, threshold=%d, defaultLeft=%d, left=%d, right=%d"
                            .formatted(nodeCount, threshold.length, defaultLeft.length, left.length, right.length));
        }
        if (leafValue.length == 0) {
            throw new IllegalArgumentException("트리에 잎이 하나도 없습니다.");
        }
        // cover(노드를 지난 학습 표본 수)는 예측에는 쓰이지 않지만 TreeSHAP에 반드시 필요하다.
        if (nodeCover.length != nodeCount || leafCover.length != leafValue.length) {
            throw new IllegalArgumentException(
                    "cover 배열 길이가 트리와 맞지 않습니다: nodeCover=%d(노드 %d), leafCover=%d(잎 %d)"
                            .formatted(nodeCover.length, nodeCount, leafCover.length, leafValue.length));
        }
    }

    public double predict(double[] features) {
        if (splitFeature.length == 0) {
            return leafValue[0];
        }
        int node = 0;
        while (node >= 0) {
            int featureIndex = splitFeature[node];
            if (featureIndex >= features.length) {
                throw new IllegalArgumentException(
                        "feature 벡터가 모델이 요구하는 길이보다 짧습니다: 필요한 인덱스=%d, 실제 길이=%d"
                                .formatted(featureIndex, features.length));
            }
            double value = features[featureIndex];
            boolean goLeft = Double.isNaN(value) ? defaultLeft[node] : value <= threshold[node];
            node = goLeft ? left[node] : right[node];
        }
        return leafValue[-node - 1];
    }

    /** 자식 인덱스 규약에 맞춰 cover를 읽는다. 음수면 잎이다. */
    public double coverOf(int childIndex) {
        return childIndex >= 0 ? nodeCover[childIndex] : leafCover[-childIndex - 1];
    }

    public int maxFeatureIndex() {
        int max = -1;
        for (int featureIndex : splitFeature) {
            max = Math.max(max, featureIndex);
        }
        return max;
    }
}
