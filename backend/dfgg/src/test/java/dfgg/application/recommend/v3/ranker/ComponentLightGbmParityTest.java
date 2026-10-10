package dfgg.application.recommend.v3.ranker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.application.recommend.component.ComponentCandidateFeatures;
import dfgg.infrastructure.config.ComponentLtrConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** 실제 학습 모델을 명시적으로 지정해 실행한다. Core 모델을 교체하지 않는다. */
@Tag("evaluation")
class ComponentLightGbmParityTest {
    // Python component_ltr.feature_schema의 v7 스키마 계약. 모델 파일에서 기대 계약을 읽으면 검증이 무의미하다.
    private static final String FINGERPRINT = ComponentLtrConfiguration.SCHEMA_FINGERPRINT;

    @Test
    void component_model_matches_native_lightgbm_scores() throws Exception {
        Path modelPath = Path.of(System.getProperty("evaluation.component.model"));
        Path fixturePath = Path.of(System.getProperty("evaluation.component.parity"));
        var mapper = new ObjectMapper();
        var fixture = mapper.readTree(fixturePath.toFile());
        var modelJson = mapper.readTree(modelPath.toFile());
        assertThat(modelJson.path("model_version").asText()).isEqualTo("component-ltr-v7");
        assertThat(modelJson.path("target_source").asText()).isEqualTo("OBSERVED_FUTURE_TARGET_ITEM");
        assertThat(fixture.path("schema_fingerprint").asText()).isEqualTo(FINGERPRINT);
        assertThat(mapper.convertValue(fixture.path("feature_names"),
                new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>() {}))
                .isEqualTo(ComponentCandidateFeatures.FEATURE_NAMES);
        assertThat(fixture.path("model_sha256").asText()).isEqualTo(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(modelPath))));
        GradientBoostedTrees model;
        // 운영과 같은 Configuration 경로로 모델을 등록하고 Bean을 조회한다.
        try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            var properties = new java.util.HashMap<String, Object>();
            properties.put("recommendation.component-ltr.model-path", modelPath.toUri().toString());
            if (modelJson.hasNonNull("preparation_lookup_sha256")) {
                properties.put("recommendation.component-ltr.preparation-lookup-path",
                        modelPath.resolveSibling("preparation-lookup.json").toUri().toString());
            }
            context.getEnvironment().getPropertySources().addFirst(
                    new org.springframework.core.env.MapPropertySource("component-test", properties));
            context.register(ComponentLtrConfiguration.class);
            context.refresh();
            model = context.getBean("componentLtrModel", GradientBoostedTrees.class);
        }
        assertThat(fixture.path("cases").size()).isGreaterThan(1000);
        assertThat(fixture.path("boundary_case_count").asInt()).isPositive();
        double maximumError = 0;
        int missingCases = 0;
        for (var testCase : fixture.path("cases")) {
            var values = testCase.path("features");
            assertThat(values.size()).isEqualTo(ComponentCandidateFeatures.FEATURE_NAMES.size());
            double[] vector = new double[values.size()];
            for (int i = 0; i < vector.length; i++) {
                vector[i] = values.get(i).isNull() ? Double.NaN : values.get(i).asDouble();
            }
            if (java.util.Arrays.stream(vector).anyMatch(Double::isNaN)) missingCases++;
            double actual = model.predict(vector);
            double expected = testCase.path("expected_score").asDouble();
            assertThat(actual).isFinite();
            assertThat(expected).isFinite();
            maximumError = Math.max(maximumError, Math.abs(actual - expected));
        }
        assertThat(missingCases).isPositive();
        assertThat(maximumError).isLessThanOrEqualTo(1e-9);
        // 기존 Core 경계로 Component 모델을 잘못 읽는 경우는 계속 거절해야 한다.
        try (var input = Files.newInputStream(modelPath)) {
            assertThatThrownBy(() -> LightGbmModelLoader.load(input)).isInstanceOf(IllegalStateException.class);
        }
        var report = java.util.Map.of("cases", fixture.path("cases").size(), "missingCases", missingCases,
                "maximumAbsoluteError", maximumError, "tolerance", 1e-9,
                "modelSha256", fixture.path("model_sha256").asText());
        mapper.writerWithDefaultPrettyPrinter().writeValue(fixturePath.resolveSibling("java-parity-report.json").toFile(), report);
        System.out.println("COMPONENT_PARITY " + report);
    }
}
