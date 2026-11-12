package dfgg.application.recommend.v3.ranker;

/**
 * 각 feature가 예측을 얼마나 밀어올리고 내렸는지 계산한다 (TreeSHAP, path-dependent).
 * <p>
 * generator가 준 점수와는 다른 값이다.
 * {@code counter_score}는 단독 NDCG@1이 0.001인데도 값 자체는 클 수 있음을 확인했다.
 * "카운터 점수가 높다"와 "카운터 때문에 순위가 올랐다"는 다른 말이고, 사용자에게 보여줄 이유는 뒤쪽이다.
 * <p>
 * 구현은 Lundberg의 path-dependent TreeSHAP이다.
 * 분기에 쓰이지 않은 feature의 몫을 노드별 cover 비율로 나누기 때문에 트리에 cover가 실려 있어야 한다.
 * LightGBM의 {@code pred_contrib=True}와 1e-6 이내로 일치함을 {@code TreeShapParityTest}가 보장한다.
 */
public class TreeShapCalculator {

    private final GradientBoostedTrees model;
    private final int featureCount;
    private final double baseValue;
    private final int maxPathLength;

    public TreeShapCalculator(GradientBoostedTrees model, int featureCount) {
        this.model = model;
        this.featureCount = featureCount;
        this.baseValue = computeBaseValue(model);
        this.maxPathLength = maxDepth(model) + 2;
    }

    public double baseValue() {
        return baseValue;
    }

    public FeatureContributions contributions(double[] features) {
        double[] phi = new double[featureCount];
        // 가지마다 경로가 독립적으로 자라지만, 깊이 d의 경로는 앞쪽 d칸만 물려받는다.
        // 그래서 노드마다 배열을 새로 만들 것 없이 한 버퍼에 깊이별 구간을 겹쳐 쓴다.
        Path path = new Path(maxPathLength);
        for (DecisionTree tree : model.trees()) {
            if (tree.splitFeature().length == 0) {
                continue;   // 잎 하나뿐인 트리는 기여가 없다. 기대값에만 들어간다.
            }
            walk(tree, 0, features, phi, path, 0, 0, 1.0, 1.0, -1);
        }
        return new FeatureContributions(phi, baseValue);
    }

    /**
     * 노드를 훑으며 잎에 도달할 때마다 경로 위 feature들에 몫을 나눠준다.
     *
     * @param childIndex   자식 인덱스 규약(0 이상이면 분기 노드, 음수면 잎)
     * @param depth        지금까지 쌓인 고유 경로 길이
     * @param zeroFraction 이 feature를 "모른다"고 볼 때 이 가지로 올 비율
     * @param oneFraction  이 feature를 "안다"고 볼 때 이 가지로 올 비율(1 또는 0)
     */
    private void walk(
            DecisionTree tree, int childIndex, double[] features, double[] phi,
            Path path, int offset, int depth,
            double zeroFraction, double oneFraction, int featureIndex
    ) {
        path.extend(offset, depth, zeroFraction, oneFraction, featureIndex);

        if (childIndex < 0) {
            double leafValue = tree.leafValue()[-childIndex - 1];
            for (int i = 1; i <= depth; i++) {
                double weight = path.unwoundSum(offset, depth, i);
                phi[path.featureIndex[offset + i]] +=
                        weight * (path.oneFraction[offset + i] - path.zeroFraction[offset + i]) * leafValue;
            }
            return;
        }

        int splitFeature = tree.splitFeature()[childIndex];
        double value = features[splitFeature];
        boolean goLeft = Double.isNaN(value)
                ? tree.defaultLeft()[childIndex]
                : value <= tree.threshold()[childIndex];
        int hot = goLeft ? tree.left()[childIndex] : tree.right()[childIndex];
        int cold = goLeft ? tree.right()[childIndex] : tree.left()[childIndex];

        double incomingZero = 1.0;
        double incomingOne = 1.0;
        int pathIndex = 0;
        while (pathIndex <= depth && path.featureIndex[offset + pathIndex] != splitFeature) {
            pathIndex++;
        }
        if (pathIndex <= depth) {
            // 같은 feature로 이미 갈랐다. 중복해서 세지 않도록 접었다가 다시 편다.
            incomingZero = path.zeroFraction[offset + pathIndex];
            incomingOne = path.oneFraction[offset + pathIndex];
            path.unwind(offset, depth, pathIndex);
            depth--;
        }

        double nodeCover = tree.nodeCover()[childIndex];
        double hotFraction = tree.coverOf(hot) / nodeCover;
        double coldFraction = tree.coverOf(cold) / nodeCover;

        int childOffset = offset + depth + 1;
        path.copyForward(offset, childOffset, depth + 1);
        walk(tree, hot, features, phi, path, childOffset, depth + 1,
                incomingZero * hotFraction, incomingOne, splitFeature);

        // 왼쪽 가지가 자기 구간을 덮어썼으므로 오른쪽에 넘길 경로를 다시 만든다.
        path.copyBackward(childOffset, offset, depth + 1);
        walk(tree, cold, features, phi, path, childOffset, depth + 1,
                incomingZero * coldFraction, 0.0, splitFeature);
    }

    /**
     * 경로를 담는 버퍼. 깊이 d의 구간은 {@code [offset, offset + d]}이고, 자식은 그 뒤에 이어 쓴다.
     * 노드마다 배열을 새로 잡지 않으려고 하나의 평탄 버퍼를 나눠 쓴다.
     */
    private static final class Path {
        private final int[] featureIndex;
        private final double[] zeroFraction;
        private final double[] oneFraction;
        private final double[] weight;

        private Path(int maxDepth) {
            int size = (maxDepth + 2) * (maxDepth + 1) / 2 + maxDepth + 2;
            featureIndex = new int[size];
            zeroFraction = new double[size];
            oneFraction = new double[size];
            weight = new double[size];
        }

        private void copyForward(int from, int to, int length) {
            System.arraycopy(featureIndex, from, featureIndex, to, length);
            System.arraycopy(zeroFraction, from, zeroFraction, to, length);
            System.arraycopy(oneFraction, from, oneFraction, to, length);
            System.arraycopy(weight, from, weight, to, length);
        }

        private void copyBackward(int from, int to, int length) {
            copyForward(to, from, length);
        }

        private void extend(int offset, int depth, double zero, double one, int feature) {
            int at = offset + depth;
            featureIndex[at] = feature;
            zeroFraction[at] = zero;
            oneFraction[at] = one;
            weight[at] = depth == 0 ? 1.0 : 0.0;

            for (int i = depth - 1; i >= 0; i--) {
                weight[offset + i + 1] += one * weight[offset + i] * (i + 1) / (double) (depth + 1);
                weight[offset + i] = zero * weight[offset + i] * (depth - i) / (double) (depth + 1);
            }
        }

        private void unwind(int offset, int depth, int pathIndex) {
            double one = oneFraction[offset + pathIndex];
            double zero = zeroFraction[offset + pathIndex];
            double nextOnePortion = weight[offset + depth];

            for (int i = depth - 1; i >= 0; i--) {
                if (one != 0.0) {
                    double previous = weight[offset + i];
                    weight[offset + i] = nextOnePortion * (depth + 1) / ((i + 1) * one);
                    nextOnePortion = previous
                            - weight[offset + i] * zero * (depth - i) / (double) (depth + 1);
                } else if (zero != 0.0) {
                    weight[offset + i] = weight[offset + i] * (depth + 1) / (zero * (depth - i));
                }
            }
            for (int i = pathIndex; i < depth; i++) {
                featureIndex[offset + i] = featureIndex[offset + i + 1];
                zeroFraction[offset + i] = zeroFraction[offset + i + 1];
                oneFraction[offset + i] = oneFraction[offset + i + 1];
            }
        }

        /** 경로에서 한 칸을 뺐을 때 남는 가중치 합. 경로 자체는 바꾸지 않는다. */
        private double unwoundSum(int offset, int depth, int pathIndex) {
            double one = oneFraction[offset + pathIndex];
            double zero = zeroFraction[offset + pathIndex];
            double nextOnePortion = weight[offset + depth];
            double total = 0.0;

            for (int i = depth - 1; i >= 0; i--) {
                if (one != 0.0) {
                    double portion = nextOnePortion * (depth + 1) / ((i + 1) * one);
                    total += portion;
                    nextOnePortion = weight[offset + i]
                            - portion * zero * (depth - i) / (double) (depth + 1);
                } else if (zero != 0.0) {
                    total += (weight[offset + i] / zero) / ((depth - i) / (double) (depth + 1));
                }
            }
            return total;
        }
    }

    /** 트리의 기대 출력. 잎 값을 cover로 가중평균한 값이다. */
    private static double computeBaseValue(GradientBoostedTrees model) {
        double total = 0.0;
        for (DecisionTree tree : model.trees()) {
            double weighted = 0.0;
            double cover = 0.0;
            for (int leaf = 0; leaf < tree.leafValue().length; leaf++) {
                weighted += tree.leafValue()[leaf] * tree.leafCover()[leaf];
                cover += tree.leafCover()[leaf];
            }
            total += cover == 0.0 ? 0.0 : weighted / cover;
        }
        return total;
    }

    private static int maxDepth(GradientBoostedTrees model) {
        int max = 0;
        for (DecisionTree tree : model.trees()) {
            // 분기가 없는 트리(부스팅 라운드가 분할을 못 찾은 경우)는 인덱스 0이 노드가 아니다.
            if (tree.splitFeature().length > 0) {
                max = Math.max(max, depthOf(tree, 0));
            }
        }
        return max;
    }

    private static int depthOf(DecisionTree tree, int childIndex) {
        if (childIndex < 0) {
            return 0;
        }
        return 1 + Math.max(depthOf(tree, tree.left()[childIndex]), depthOf(tree, tree.right()[childIndex]));
    }

}
