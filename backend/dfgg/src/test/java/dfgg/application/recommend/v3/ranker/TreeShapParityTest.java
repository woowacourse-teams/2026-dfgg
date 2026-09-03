package dfgg.application.recommend.v3.ranker;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.application.recommend.v3.feature.FeatureName;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Java TreeSHAP이 LightGBM과 같은 기여도를 내는지 확인하는 하드 게이트.
 * <p>
 * 기준값은 참조 구현이 아니라 LightGBM 자체({@code pred_contrib=True})다.
 * TreeSHAP을 Python과 Java에 두 번 구현하면 둘 다 같은 방식으로 틀릴 수 있어,
 * 학습 때 저장한 네이티브 모델에서 직접 뽑았다.
 */
class TreeShapParityTest {

    /** 기여도는 사용자에게 보여줄 값이라, 순서가 뒤집히지 않을 만큼은 맞아야 한다. */
    private static final double TOLERANCE = 1e-6;

    private static TreeShapCalculator calculator;
    private static GradientBoostedTrees model;
    private static JsonNode fixture;

    @BeforeAll
    static void loadArtifacts() {
        model = LightGbmModelLoader.loadFromClasspath("ltr/model.json");
        calculator = new TreeShapCalculator(model, FeatureName.values().length);
        fixture = readJson("ltr/shap_parity_sample.json");
    }

    private static JsonNode readJson(String resourcePath) {
        try (InputStream input = TreeShapParityTest.class.getClassLoader()
                .getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IllegalStateException("리소스를 찾을 수 없습니다: " + resourcePath);
            }
            return new ObjectMapper().readTree(input);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static double[] featuresOf(JsonNode testCase) {
        JsonNode values = testCase.get("features");
        double[] features = new double[values.size()];
        for (int index = 0; index < features.length; index++) {
            features[index] = values.get(index).isNull() ? Double.NaN : values.get(index).asDouble();
        }
        return features;
    }

    @Test
    @DisplayName("fixture가 현재 스키마로 만들어졌다")
    void fixture_MatchesCurrentSchema() {
        assertThat(fixture.get("schema_fingerprint").asText())
                .isEqualTo(FeatureName.schemaFingerprint());
        assertThat(fixture.get("feature_count").asInt()).isEqualTo(FeatureName.values().length);
        assertThat(fixture.get("cases")).isNotEmpty();
    }

    @Test
    @DisplayName("모든 케이스에서 feature별 기여도가 LightGBM과 1e-6 이내로 일치한다")
    void contributions_MatchLightGbmPredContrib() {
        List<String> mismatches = new ArrayList<>();

        for (JsonNode testCase : fixture.get("cases")) {
            JsonNode expected = testCase.get("expected_contributions");
            double[] actual = calculator.contributions(featuresOf(testCase)).values();

            for (int index = 0; index < actual.length; index++) {
                double difference = Math.abs(actual[index] - expected.get(index).asDouble());
                if (difference > TOLERANCE) {
                    mismatches.add("%s: 기대=%.9f 실제=%.9f 차이=%.2e".formatted(
                            FeatureName.values()[index], expected.get(index).asDouble(),
                            actual[index], difference));
                }
            }
        }

        assertThat(mismatches).isEmpty();
    }

    @Test
    @DisplayName("기준값이 LightGBM의 base value와 일치한다 — 마지막 칸이 그 값이다")
    void baseValue_MatchesLightGbmBaseValue() {
        JsonNode expected = fixture.get("cases").get(0).get("expected_contributions");
        double expectedBase = expected.get(expected.size() - 1).asDouble();

        assertThat(calculator.baseValue()).isCloseTo(expectedBase, org.assertj.core.data.Offset.offset(TOLERANCE));
    }

    @Test
    @DisplayName("기여도 합에 기준값을 더하면 예측값이 된다 — 실제 모델에서도 성립한다")
    void contributions_PlusBaseValue_EqualThePrediction() {
        List<String> violations = new ArrayList<>();

        for (JsonNode testCase : fixture.get("cases")) {
            double[] features = featuresOf(testCase);
            FeatureContributions contributions = calculator.contributions(features);

            double total = contributions.baseValue();
            for (double value : contributions.values()) {
                total += value;
            }
            double prediction = model.predict(features);
            if (Math.abs(total - prediction) > TOLERANCE) {
                violations.add("합=%.9f 예측=%.9f".formatted(total, prediction));
            }
        }

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("결측이 섞인 케이스가 실제로 들어 있다")
    void fixture_ContainsRowsWithMissingValues() {
        long withMissing = 0;
        for (JsonNode testCase : fixture.get("cases")) {
            for (JsonNode value : testCase.get("features")) {
                if (value.isNull()) {
                    withMissing++;
                    break;
                }
            }
        }

        assertThat(withMissing).isGreaterThan(0);
    }
}
