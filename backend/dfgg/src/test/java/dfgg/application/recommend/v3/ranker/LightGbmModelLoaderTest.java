package dfgg.application.recommend.v3.ranker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dfgg.application.recommend.v3.feature.FeatureName;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Python이 내보낸 모델을 읽는다.
 *
 * <p>검증이 이 클래스의 본체다. feature 순서가 어긋난 모델은 예외를 던지지 않고 <b>다른 feature를
 * 읽으면서 그럴듯한 점수를 낸다.</b> 그래서 로딩 시점에 스키마를 대조해 기동 자체를 실패시킨다.
 */
class LightGbmModelLoaderTest {

    private static String namesJson(List<String> names) {
        return names.stream().map(name -> "\"" + name + "\"").collect(Collectors.joining(", "));
    }

    private static String modelJson(String fingerprint, List<String> featureNames, String trees) {
        return """
                {
                  "schema_fingerprint": "%s",
                  "feature_names": [%s],
                  "objective": "lambdarank",
                  "trees": %s
                }
                """.formatted(fingerprint, namesJson(featureNames), trees);
    }

    /** f0 <= 0.5 → 1.0, 아니면 2.0. */
    private static final String ONE_TREE = """
            [{"split_feature": [0], "threshold": [0.5], "default_left": [true],
              "left": [-1], "right": [-2], "leaf_value": [1.0, 2.0],
              "node_cover": [10.0], "leaf_cover": [6.0, 4.0]}]
            """;

    private static InputStream stream(String json) {
        return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String validModel() {
        return modelJson(FeatureName.schemaFingerprint(), FeatureName.exportNames(), ONE_TREE);
    }

    @Test
    @DisplayName("스키마가 맞는 모델을 읽어 예측까지 한다")
    void load_WhenSchemaMatches_ReturnsUsableModel() {
        GradientBoostedTrees model = LightGbmModelLoader.load(stream(validModel()));

        double[] features = new double[FeatureName.values().length];
        features[0] = 0.1;
        assertThat(model.trees()).hasSize(1);
        assertThat(model.predict(features)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("지문이 다르면 거부한다 — 스키마가 바뀐 채 학습된 모델이다")
    void load_WhenFingerprintDiffers_ThrowsException() {
        String json = modelJson("deadbeefdeadbeef", FeatureName.exportNames(), ONE_TREE);

        assertThatThrownBy(() -> LightGbmModelLoader.load(stream(json)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("deadbeefdeadbeef");
    }

    @Test
    @DisplayName("feature 이름 순서가 어긋나면 거부한다 — 조용히 다른 feature를 읽는 상황을 막는다")
    void load_WhenFeatureNamesAreReordered_ThrowsException() {
        List<String> swapped = new java.util.ArrayList<>(FeatureName.exportNames());
        swapped.add(0, swapped.remove(1));
        String json = modelJson(FeatureName.schemaFingerprint(), swapped, ONE_TREE);

        assertThatThrownBy(() -> LightGbmModelLoader.load(stream(json)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("feature");
    }

    @Test
    @DisplayName("feature 개수가 다르면 거부한다")
    void load_WhenFeatureCountDiffers_ThrowsException() {
        List<String> truncated = FeatureName.exportNames().subList(0, FeatureName.values().length - 1);
        String json = modelJson(FeatureName.schemaFingerprint(), truncated, ONE_TREE);

        assertThatThrownBy(() -> LightGbmModelLoader.load(stream(json)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("스키마 범위를 벗어난 feature 인덱스로 분기하는 모델은 거부한다")
    void load_WhenTreeSplitsOnUnknownFeatureIndex_ThrowsException() {
        String outOfRange = """
                [{"split_feature": [999], "threshold": [0.5], "default_left": [true],
                  "left": [-1], "right": [-2], "leaf_value": [1.0, 2.0],
              "node_cover": [10.0], "leaf_cover": [6.0, 4.0]}]
                """;
        String json = modelJson(FeatureName.schemaFingerprint(), FeatureName.exportNames(), outOfRange);

        assertThatThrownBy(() -> LightGbmModelLoader.load(stream(json)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("트리가 비어 있는 모델은 거부한다")
    void load_WhenNoTrees_ThrowsException() {
        String json = modelJson(FeatureName.schemaFingerprint(), FeatureName.exportNames(), "[]");

        assertThatThrownBy(() -> LightGbmModelLoader.load(stream(json)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("실제로 커밋된 model.json이 현재 스키마로 로드된다 — 학습/추론 계약의 최종 확인")
    void load_WhenReadingCommittedModelResource_Succeeds() {
        GradientBoostedTrees model = LightGbmModelLoader.loadFromClasspath("ltr/model.json");

        assertThat(model.trees()).isNotEmpty();
        assertThat(model.maxFeatureIndex()).isLessThan(FeatureName.values().length);
    }
}
