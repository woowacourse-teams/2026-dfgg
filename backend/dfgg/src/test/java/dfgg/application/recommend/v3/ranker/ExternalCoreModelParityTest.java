package dfgg.application.recommend.v3.ranker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.application.recommend.v3.feature.FeatureName;
import dfgg.application.recommend.v3.feature.FeatureSchemaFingerprint;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * 저장소 밖에 있는 Core 모델이 계약대로 읽히고, Java가 Python과 같은 점수를 내는지 확인한다.
 *
 * <p>{@link LightGbmParityTest}는 저장소 안의 모델과 fixture만 읽는다. 지금 스키마 전체로 학습한 모델은
 * 저장소에 넣지 않으므로, 그 모델을 확인하려면 저장소의 파일을 덮어써야 했다. 이 테스트는 모델과 fixture의
 * 경로를 실행할 때 받아 그럴 필요를 없앤다. 스프링을 띄우지 않고 DB에도 붙지 않는다.
 *
 * <p>기본은 지금 스키마 전체로 읽는다. 운영 모델처럼 기준 계약(스키마의 앞쪽 feature)으로 학습한 모델은
 * {@code -Devaluation.core.contract=baseline}을 더해 기준 계약으로 읽는다.
 *
 * <pre>
 * # backend/ml — 모델과 같은 학습 데이터로 fixture를 만든다
 * uv run python -m dfgg_ltr.export_parity_sample --model &lt;model.json&gt; --data data/train.jsonl \
 *     --out &lt;parity.json&gt; --count 1000
 * # backend/dfgg
 * ./gradlew evaluationTest --tests '*ExternalCoreModelParityTest' \
 *     -Devaluation.core.model=&lt;model.json 절대경로&gt; -Devaluation.core.parity=&lt;parity.json 절대경로&gt; \
 *     [-Devaluation.core.contract=baseline]
 * </pre>
 */
@Tag("evaluation")
class ExternalCoreModelParityTest {

    private static final String MODEL_PROPERTY = "evaluation.core.model";
    private static final String PARITY_PROPERTY = "evaluation.core.parity";
    private static final String CONTRACT_PROPERTY = "evaluation.core.contract";
    private static final String BASELINE_CONTRACT = "baseline";
    private static final String CURRENT_CONTRACT = "current";

    /** 추천은 점수의 대소만 쓰지만, 허용 오차를 크게 두면 순서가 뒤집혀도 통과한다. */
    private static final double TOLERANCE = 1e-6;

    private static GradientBoostedTrees model;
    private static JsonNode fixture;
    private static boolean baselineContract;

    @BeforeAll
    static void loadArtifacts() throws IOException {
        String modelPath = System.getProperty(MODEL_PROPERTY);
        String parityPath = System.getProperty(PARITY_PROPERTY);
        // 경로를 주지 않으면 건너뛴다. 평가 테스트를 한꺼번에 돌리는 다른 실행이 이 테스트 때문에 실패하지 않게 한다.
        assumeTrue(modelPath != null && parityPath != null,
                "-D%s, -D%s 로 모델과 fixture의 경로를 주어야 한다".formatted(MODEL_PROPERTY, PARITY_PROPERTY));

        String contract = System.getProperty(CONTRACT_PROPERTY, CURRENT_CONTRACT);
        assertThat(contract)
                .as("-D%s 는 %s 또는 %s 여야 한다".formatted(CONTRACT_PROPERTY, CURRENT_CONTRACT, BASELINE_CONTRACT))
                .isIn(CURRENT_CONTRACT, BASELINE_CONTRACT);
        baselineContract = BASELINE_CONTRACT.equals(contract);

        try (InputStream input = Files.newInputStream(Path.of(modelPath))) {
            // 고른 계약과 대조한다. 다른 계약의 모델을 넘기면 여기서 거부된다.
            model = baselineContract ? LightGbmModelLoader.loadBaseline(input) : LightGbmModelLoader.load(input);
        }
        fixture = new ObjectMapper().readTree(Path.of(parityPath).toFile());
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
    @DisplayName("fixture가 모델과 같은 계약으로 만들어졌다 — 다른 모델의 fixture로 통과하는 일을 막는다")
    void fixture_WhenMadeFromTheSameModel_MatchesTheContract() {
        // given
        String expectedFingerprint = baselineContract
                ? FeatureSchemaFingerprint.BASELINE : FeatureSchemaFingerprint.EXPECTED;
        int expectedFeatureCount = baselineContract
                ? FeatureName.baselineExportNames().size() : FeatureName.values().length;

        // when & then
        assertThat(fixture.get("schema_fingerprint").asText()).isEqualTo(expectedFingerprint);
        assertThat(fixture.get("feature_names")).hasSize(expectedFeatureCount);
        assertThat(fixture.get("cases")).isNotEmpty();
    }

    @Test
    @DisplayName("모든 케이스에서 Java 예측이 Python 예측과 1e-6 이내로 일치한다")
    void predict_WhenGivenFixtureCases_MatchesPythonReferenceScores() {
        // given
        List<String> mismatches = new ArrayList<>();

        // when
        for (JsonNode testCase : fixture.get("cases")) {
            double expected = testCase.get("expected_score").asDouble();
            double actual = model.predict(featuresOf(testCase));
            if (Math.abs(actual - expected) > TOLERANCE) {
                mismatches.add("기대=%.12f 실제=%.12f 차이=%.3e".formatted(
                        expected, actual, Math.abs(actual - expected)));
            }
        }

        // then
        System.out.printf("[parity] 케이스 %d개 비교, 불일치 %d개%n", fixture.get("cases").size(), mismatches.size());
        assertThat(mismatches).isEmpty();
    }

    @Test
    @DisplayName("상황을 모르는 케이스가 들어 있다 — 기준 계약의 칸에는 값이 있고 게임 상황 칸은 모두 빈 행")
    void fixture_WhenModelReadsSituationFeatures_ContainsRowsWithUnknownSituation() {
        // given: 기준 계약의 모델은 게임 상황 칸을 읽지 않으므로 이 확인이 필요 없다
        assumeFalse(baselineContract, "기준 계약의 모델에는 게임 상황 칸이 없다");
        int baselineCount = FeatureName.baselineExportNames().size();

        // when: 앞쪽에 값이 하나라도 있고, 기준 계약 뒤의 칸이 전부 빈 케이스를 센다
        long unknownSituation = 0;
        for (JsonNode testCase : fixture.get("cases")) {
            double[] features = featuresOf(testCase);
            boolean hasBaselineValue = false;
            boolean situationEmpty = true;
            for (int index = 0; index < features.length; index++) {
                if (index < baselineCount) {
                    hasBaselineValue |= !Double.isNaN(features[index]);
                } else {
                    situationEmpty &= Double.isNaN(features[index]);
                }
            }
            if (hasBaselineValue && situationEmpty) {
                unknownSituation++;
            }
        }

        // then
        assertThat(unknownSituation)
                .as("상황을 모르는 행이 표본에 없다. export_parity_sample의 --count를 늘려 다시 만든다")
                .isGreaterThan(0);
    }

    @Test
    @DisplayName("등호 경계를 때리는 케이스가 들어 있다 — 없으면 `<`로 잘못 구현해도 통과한다")
    void fixture_WhenMadeByTheExportTool_ContainsThresholdBoundaryCases() {
        // when & then
        assertThat(fixture.get("boundary_case_count").asInt()).isGreaterThan(0);
    }

    @Test
    @DisplayName("케이스들의 점수가 서로 다르다 — 상수를 내도 통과하는 게이트는 게이트가 아니다")
    void predict_WhenGivenFixtureCases_ProducesDistinctScores() {
        // given
        List<Double> scores = new ArrayList<>();

        // when
        for (JsonNode testCase : fixture.get("cases")) {
            scores.add(model.predict(featuresOf(testCase)));
        }

        // then
        assertThat(scores.stream().distinct().count()).isGreaterThan(10);
    }
}
