package dfgg.application.recommend.v3.ranker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dfgg.application.recommend.v3.feature.FeatureName;
import dfgg.application.recommend.v3.feature.FeatureSchemaFingerprint;
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
    @DisplayName("기준 계약으로 학습된 모델을 읽는다 — 지금 운영 모델이 이 계약이다")
    void loadBaseline_WhenModelFollowsBaselineContract_ReturnsUsableModel() {
        // given
        String json = modelJson(FeatureSchemaFingerprint.BASELINE, FeatureName.baselineExportNames(), ONE_TREE);
        double[] features = new double[FeatureName.values().length];
        features[0] = 0.1;

        // when
        GradientBoostedTrees model = LightGbmModelLoader.loadBaseline(stream(json));

        // then
        assertThat(model.predict(features)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("기준 모델은 기준 계약 뒤의 칸을 읽지 않는다 — 스키마가 늘어 벡터가 길어져도 점수가 그대로다")
    void loadBaseline_WhenVectorIsLongerThanBaseline_PredictsTheSameScore() {
        // given: 기준 계약의 마지막 feature로 갈리는 트리. 그 칸까지는 읽고 그 뒤는 읽지 않아야 한다.
        int baselineCount = FeatureName.baselineExportNames().size();
        String splitsOnLastBaselineFeature = """
                [{"split_feature": [%d], "threshold": [0.5], "default_left": [true],
                  "left": [-1], "right": [-2], "leaf_value": [1.0, 2.0],
                  "node_cover": [10.0], "leaf_cover": [6.0, 4.0]}]
                """.formatted(baselineCount - 1);
        GradientBoostedTrees model = LightGbmModelLoader.loadBaseline(stream(modelJson(
                FeatureSchemaFingerprint.BASELINE, FeatureName.baselineExportNames(), splitsOnLastBaselineFeature)));

        double[] baselineVector = new double[baselineCount];
        baselineVector[baselineCount - 1] = 0.9;
        double[] longerVector = java.util.Arrays.copyOf(baselineVector, baselineCount + 20);
        java.util.Arrays.fill(longerVector, baselineCount, longerVector.length, 123.0);

        // when
        double baselineScore = model.predict(baselineVector);
        double longerScore = model.predict(longerVector);

        // then
        assertThat(baselineScore).isEqualTo(2.0);
        assertThat(longerScore).isEqualTo(baselineScore);
    }

    @Test
    @DisplayName("기준 계약과 지문이 다른 모델은 기준 모델로 읽지 않는다")
    void loadBaseline_WhenFingerprintDiffers_ThrowsException() {
        // given
        String json = modelJson("deadbeefdeadbeef", FeatureName.baselineExportNames(), ONE_TREE);

        // when & then
        assertThatThrownBy(() -> LightGbmModelLoader.loadBaseline(stream(json)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("deadbeefdeadbeef");
    }

    @Test
    @DisplayName("기준 계약보다 feature가 많은 모델은 기준 모델로 읽지 않는다 — 다른 계약으로 학습된 모델이다")
    void loadBaseline_WhenModelHasMoreFeaturesThanBaseline_ThrowsException() {
        // given
        List<String> extended = new java.util.ArrayList<>(FeatureName.baselineExportNames());
        extended.add("feature_added_after_baseline");
        String json = modelJson(FeatureSchemaFingerprint.BASELINE, extended, ONE_TREE);

        // when & then
        assertThatThrownBy(() -> LightGbmModelLoader.loadBaseline(stream(json)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("실제로 커밋된 model.json이 기준 계약으로 로드된다 — 학습/추론 계약의 최종 확인")
    void load_WhenReadingCommittedModelResource_Succeeds() {
        GradientBoostedTrees model = LightGbmModelLoader.loadFromClasspath("ltr/model.json");

        assertThat(model.trees()).isNotEmpty();
        assertThat(model.maxFeatureIndex()).isLessThan(FeatureName.values().length);
    }
}
