package dfgg.infrastructure.config;

import dfgg.application.recommend.component.ComponentCandidateFeatures;
import dfgg.application.recommend.component.ComponentRanker;
import dfgg.application.recommend.v3.ranker.GradientBoostedTrees;
import dfgg.application.recommend.v3.ranker.LightGbmModelLoader;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

/** 외부에서 학습한 Component 모델의 위치 선택·로딩·Bean 등록을 담당한다. */
@Configuration
@org.springframework.context.annotation.Import(PreparationLookupConfiguration.class)
@ConditionalOnProperty(name = "recommendation.component-ltr.model-path")
public class ComponentLtrConfiguration {
    // Python component_ltr.feature_schema의 v7 계약. 모델이 스스로 제시한 지문을 기대값으로 사용하지 않는다.
    public static final String SCHEMA_FINGERPRINT = "6f287c3c7dadb29c739cde1de020523db5136e80a29c71388523e47e2a7e49b3";

    @Bean
    public dfgg.application.recommend.component.ComponentRecommendationService componentRecommendations(
            @org.springframework.beans.factory.annotation.Qualifier("componentLtrModel") GradientBoostedTrees model, dfgg.domain.match.PreparationLookup lookup) {
        return new dfgg.application.recommend.component.ComponentRecommendationService(new ComponentRanker(model), lookup);
    }

    /** file:/절대경로/model.json 또는 classpath:...를 지정하면 기동 시 계약을 검증한다. */
    @Bean
    public GradientBoostedTrees componentLtrModel(
            @Value("${recommendation.component-ltr.model-path}") Resource modelResource, dfgg.domain.match.PreparationLookup lookup) {
        try (var input = modelResource.getInputStream()) {
            byte[] bytes = input.readAllBytes();
            var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(bytes);
            String expected = json.path("preparation_lookup_sha256").isTextual() ? json.get("preparation_lookup_sha256").asText() : null;
            if (!java.util.Objects.equals(expected, lookup.sha256())) {
                throw new IllegalArgumentException("Component 모델과 Preparation Lookup SHA-256 불일치");
            }
            if (lookup.enabled() && !"component-preparation-v2".equals(json.path("preparation_lookup_schema_version").asText())) {
                throw new IllegalArgumentException("Component 모델의 Preparation Lookup schema 불일치");
            }
            return LightGbmModelLoader.load(new java.io.ByteArrayInputStream(bytes), ComponentCandidateFeatures.FEATURE_NAMES, SCHEMA_FINGERPRINT);
        } catch (IOException exception) {
            throw new UncheckedIOException("Component LTR 모델을 읽지 못했습니다: " + modelResource, exception);
        }
    }
}
