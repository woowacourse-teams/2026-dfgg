package dfgg.application.recommend.v3.ranker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.application.recommend.v3.feature.FeatureName;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code ml/dfgg_ltr/train.py}가 내보낸 모델 JSON을 읽는다.
 *
 * <p>읽기보다 <b>검증</b>이 본체다. feature 순서가 어긋난 모델은 예외 없이 다른 feature를 읽으면서
 * 그럴듯한 점수를 내기 때문에, 스키마 지문과 feature 이름을 로딩 시점에 대조해 기동을 실패시킨다.
 * 잘못된 추천을 조용히 계속 내보내는 것보다 뜨지 않는 편이 낫다.
 */
public final class LightGbmModelLoader {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private LightGbmModelLoader() {
    }

    public static GradientBoostedTrees loadFromClasspath(String resourcePath) {
        try (InputStream input = LightGbmModelLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IllegalStateException("LTR 모델 리소스를 찾을 수 없습니다: " + resourcePath);
            }
            return load(input);
        } catch (IOException exception) {
            throw new UncheckedIOException("LTR 모델을 읽지 못했습니다: " + resourcePath, exception);
        }
    }

    public static GradientBoostedTrees load(InputStream input) {
        JsonNode root = readJson(input);
        verifyFingerprint(root);
        verifyFeatureNames(root);

        GradientBoostedTrees model = new GradientBoostedTrees(readTrees(root));
        verifyFeatureIndexRange(model);
        return model;
    }

    private static JsonNode readJson(InputStream input) {
        try {
            return OBJECT_MAPPER.readTree(input);
        } catch (IOException exception) {
            throw new IllegalStateException("LTR 모델 JSON을 파싱하지 못했습니다.", exception);
        }
    }

    private static void verifyFingerprint(JsonNode root) {
        String fingerprint = root.path("schema_fingerprint").asText(null);
        if (!FeatureName.schemaFingerprint().equals(fingerprint)) {
            throw new IllegalStateException(
                    "모델의 feature 스키마 지문이 현재 코드와 다릅니다. 모델=%s, 코드=%s. 모델을 다시 학습하세요."
                            .formatted(fingerprint, FeatureName.schemaFingerprint()));
        }
    }

    private static void verifyFeatureNames(JsonNode root) {
        List<String> expected = FeatureName.exportNames();
        JsonNode names = root.path("feature_names");
        if (!names.isArray() || names.size() != expected.size()) {
            throw new IllegalStateException(
                    "모델의 feature 개수가 현재 스키마와 다릅니다. 모델=%d, 코드=%d."
                            .formatted(names.size(), expected.size()));
        }
        for (int index = 0; index < expected.size(); index++) {
            String actual = names.get(index).asText();
            if (!expected.get(index).equals(actual)) {
                throw new IllegalStateException(
                        "모델의 feature 순서가 현재 스키마와 다릅니다. index=%d, 모델=%s, 코드=%s."
                                .formatted(index, actual, expected.get(index)));
            }
        }
    }

    private static List<DecisionTree> readTrees(JsonNode root) {
        JsonNode trees = root.path("trees");
        if (!trees.isArray() || trees.isEmpty()) {
            throw new IllegalStateException("모델에 트리가 하나도 없습니다.");
        }
        List<DecisionTree> parsed = new ArrayList<>(trees.size());
        for (JsonNode tree : trees) {
            parsed.add(new DecisionTree(
                    intArray(tree, "split_feature"),
                    doubleArray(tree, "threshold"),
                    booleanArray(tree, "default_left"),
                    intArray(tree, "left"),
                    intArray(tree, "right"),
                    doubleArray(tree, "leaf_value"),
                    doubleArray(tree, "node_cover"),
                    doubleArray(tree, "leaf_cover")));
        }
        return parsed;
    }

    private static void verifyFeatureIndexRange(GradientBoostedTrees model) {
        int featureCount = FeatureName.values().length;
        if (model.maxFeatureIndex() >= featureCount) {
            throw new IllegalStateException(
                    "모델이 스키마에 없는 feature 인덱스로 분기합니다: %d (feature 개수=%d)."
                            .formatted(model.maxFeatureIndex(), featureCount));
        }
    }

    private static int[] intArray(JsonNode tree, String field) {
        JsonNode array = requireArray(tree, field);
        int[] values = new int[array.size()];
        for (int index = 0; index < values.length; index++) {
            values[index] = array.get(index).asInt();
        }
        return values;
    }

    private static double[] doubleArray(JsonNode tree, String field) {
        JsonNode array = requireArray(tree, field);
        double[] values = new double[array.size()];
        for (int index = 0; index < values.length; index++) {
            values[index] = array.get(index).asDouble();
        }
        return values;
    }

    private static boolean[] booleanArray(JsonNode tree, String field) {
        JsonNode array = requireArray(tree, field);
        boolean[] values = new boolean[array.size()];
        for (int index = 0; index < values.length; index++) {
            values[index] = array.get(index).asBoolean();
        }
        return values;
    }

    private static JsonNode requireArray(JsonNode tree, String field) {
        JsonNode array = tree.path(field);
        if (!array.isArray()) {
            throw new IllegalStateException("트리에 %s 배열이 없습니다.".formatted(field));
        }
        return array;
    }
}
