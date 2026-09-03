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
 * Python과 Java가 같은 점수를 내는지 확인하는 하드 게이트.
 *
 * <p>고리는 둘이다. LightGBM ≡ Python 참조 순회는 {@code ml/tests/test_flatten_equivalence.py}가
 * 1e-9로 보장하고, Python 참조 순회 ≡ Java는 이 테스트가 보장한다. 두 고리가 이어져
 * "LightGBM으로 학습한 점수 == 운영 중 Java가 내는 점수"가 성립한다.
 *
 * <p>fixture는 Java가 로드하는 것과 <b>같은</b> {@code model.json}에서 만들었다. 그래서 여기가
 * 깨지면 원인은 모델 형식이 아니라 Java 순회다.
 */
class LightGbmParityTest {

    /** 추천은 점수의 대소만 쓰지만, 허용 오차를 크게 두면 순서가 뒤집혀도 통과한다. */
    private static final double TOLERANCE = 1e-6;

    private static GradientBoostedTrees model;
    private static JsonNode fixture;

    @BeforeAll
    static void loadArtifacts() {
        model = LightGbmModelLoader.loadFromClasspath("ltr/model.json");
        fixture = readJson("ltr/parity_sample.json");
    }

    private static JsonNode readJson(String resourcePath) {
        try (InputStream input = LightGbmParityTest.class.getClassLoader()
                .getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IllegalStateException("리소스를 찾을 수 없습니다: " + resourcePath);
            }
            return new ObjectMapper().readTree(input);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** JSON에는 NaN 리터럴이 없어 결측이 null로 실려 있다. 0으로 읽으면 안 된다. */
    private static double[] featuresOf(JsonNode testCase) {
        JsonNode values = testCase.get("features");
        double[] features = new double[values.size()];
        for (int index = 0; index < features.length; index++) {
            JsonNode value = values.get(index);
            features[index] = value.isNull() ? Double.NaN : value.asDouble();
        }
        return features;
    }

    @Test
    @DisplayName("fixture가 현재 스키마로 만들어졌다 — 낡은 fixture로 통과하는 일을 막는다")
    void fixture_MatchesCurrentSchema() {
        assertThat(fixture.get("schema_fingerprint").asText())
                .isEqualTo(FeatureName.schemaFingerprint());
        assertThat(fixture.get("cases")).isNotEmpty();
    }

    @Test
    @DisplayName("모든 케이스에서 Java 예측이 Python 예측과 1e-6 이내로 일치한다")
    void predict_MatchesPythonReferenceScores() {
        List<String> mismatches = new ArrayList<>();

        for (JsonNode testCase : fixture.get("cases")) {
            double expected = testCase.get("expected_score").asDouble();
            double actual = model.predict(featuresOf(testCase));
            if (Math.abs(actual - expected) > TOLERANCE) {
                mismatches.add("기대=%.12f 실제=%.12f 차이=%.3e".formatted(
                        expected, actual, Math.abs(actual - expected)));
            }
        }

        assertThat(mismatches).isEmpty();
    }

    @Test
    @DisplayName("결측이 섞인 케이스가 실제로 들어 있다 — default_left 경로를 밟지 않으면 게이트가 헐겁다")
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

    @Test
    @DisplayName("등호 경계를 때리는 케이스가 들어 있다 — 없으면 `<`로 잘못 구현해도 통과한다")
    void fixture_ContainsThresholdBoundaryCases() {
        // 실데이터 표본만으로는 feature 값이 임계값과 정확히 같아지는 일이 거의 없어
        // 경계가 한 번도 밟히지 않는다. 변이 테스트로 확인한 실제 구멍이다.
        assertThat(fixture.get("boundary_case_count").asInt()).isGreaterThan(0);
    }

    @Test
    @DisplayName("케이스들의 점수가 서로 다르다 — 상수를 내도 통과하는 게이트는 게이트가 아니다")
    void fixture_ScoresAreNotAllIdentical() {
        List<Double> scores = new ArrayList<>();
        for (JsonNode testCase : fixture.get("cases")) {
            scores.add(model.predict(featuresOf(testCase)));
        }

        assertThat(scores.stream().distinct().count()).isGreaterThan(10);
    }
}
