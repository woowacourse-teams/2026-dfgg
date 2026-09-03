package dfgg.application.recommend.v3.ranker;

import java.util.List;

/** 부스팅된 트리 앙상블. 예측은 모든 트리의 잎 값 합이다. */
public record GradientBoostedTrees(List<DecisionTree> trees) {

    public GradientBoostedTrees {
        if (trees.isEmpty()) {
            throw new IllegalArgumentException("트리가 하나도 없는 모델은 사용할 수 없습니다.");
        }
        trees = List.copyOf(trees);
    }

    public double predict(double[] features) {
        double sum = 0.0;
        for (DecisionTree tree : trees) {
            sum += tree.predict(features);
        }
        return sum;
    }

    public int maxFeatureIndex() {
        int max = -1;
        for (DecisionTree tree : trees) {
            max = Math.max(max, tree.maxFeatureIndex());
        }
        return max;
    }
}
