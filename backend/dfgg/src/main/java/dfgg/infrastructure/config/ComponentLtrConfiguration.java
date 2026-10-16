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
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.Resource;

/**
 * 외부에서 학습한 Component 모델의 위치 선택·로딩·Bean 등록을 담당한다.
 * 지연 Bean은 ComponentRuntimeConfiguration의 실패 격리 구간에서 최초 로딩한다.
 */
@Configuration
@org.springframework.context.annotation.Import(ComponentPolicyLookupConfiguration.class)
@ConditionalOnProperty(name = "recommendation.component-ltr.model-path")
public class ComponentLtrConfiguration {
    // Python component_ltr.feature_schema의 v9 계약. 모델이 스스로 제시한 지문을 기대값으로 사용하지 않는다.
    public static final String SCHEMA_FINGERPRINT = "4ad69fefc4cea9ebca4ad15fb4e4c4f552fec1fe11ffb4e97d52441a0565e6b5";

    @Bean
    @Lazy
    public dfgg.application.recommend.component.ComponentRecommendationService componentRecommendations(
            @org.springframework.beans.factory.annotation.Qualifier("componentLtrModel") GradientBoostedTrees model, dfgg.domain.match.ComponentPolicyLookup lookup) {
        return new dfgg.application.recommend.component.ComponentRecommendationService(new ComponentRanker(model), lookup);
    }

    /** file:/절대경로/model.json 또는 classpath:...를 지정하면 기동 시 계약을 검증한다. */
    @Bean
    @Lazy
    public GradientBoostedTrees componentLtrModel(
            @Value("${recommendation.component-ltr.model-path}") Resource modelResource, dfgg.domain.match.ComponentPolicyLookup lookup) {
        try (var input = modelResource.getInputStream()) {
            byte[] bytes = input.readAllBytes();
            var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(bytes);
            String expected = json.path("policy_lookup_sha256").isTextual() ? json.get("policy_lookup_sha256").asText() : null;
            if (!java.util.Objects.equals(expected, lookup.sha256())) {
                throw new IllegalArgumentException("Component 모델과 정책 조회표 SHA-256 불일치");
            }
            if (lookup.enabled() && !dfgg.infrastructure.recommend.ComponentPolicyLookupLoader.SCHEMA_VERSION
                    .equals(json.path("policy_lookup_schema_version").asText())) {
                throw new IllegalArgumentException("Component 모델의 정책 조회표 schema 불일치");
            }
            return LightGbmModelLoader.load(new java.io.ByteArrayInputStream(bytes), ComponentCandidateFeatures.FEATURE_NAMES, SCHEMA_FINGERPRINT);
        } catch (IOException exception) {
            throw new UncheckedIOException("Component LTR 모델을 읽지 못했습니다: " + modelResource, exception);
        }
    }
}
