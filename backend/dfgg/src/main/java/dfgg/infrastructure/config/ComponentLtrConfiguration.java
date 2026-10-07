package dfgg.infrastructure.config;

import dfgg.application.recommend.component.ComponentCandidateFeatures;
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
@ConditionalOnProperty(name = "recommendation.component-ltr.model-path")
public class ComponentLtrConfiguration {
    // Python component_ltr.feature_schema의 v5 계약. 모델이 스스로 제시한 지문을 기대값으로 사용하지 않는다.
    public static final String SCHEMA_FINGERPRINT = "54d9de4694c4f106003206c2489c3ba8cdd0346aad3c8c375fed32a4682e9188";

    @Bean
    public dfgg.application.recommend.component.ComponentRecommendationService componentRecommendations(
            @org.springframework.beans.factory.annotation.Qualifier("componentLtrModel") GradientBoostedTrees model) {
        return new dfgg.application.recommend.component.ComponentRecommendationService(model);
    }

    /** file:/절대경로/model.json 또는 classpath:...를 지정하면 기동 시 계약을 검증한다. */
    @Bean
    public GradientBoostedTrees componentLtrModel(
            @Value("${recommendation.component-ltr.model-path}") Resource modelResource) {
        try (var input = modelResource.getInputStream()) {
            return LightGbmModelLoader.load(input, ComponentCandidateFeatures.FEATURE_NAMES, SCHEMA_FINGERPRINT);
        } catch (IOException exception) {
            throw new UncheckedIOException("Component LTR 모델을 읽지 못했습니다: " + modelResource, exception);
        }
    }
}
