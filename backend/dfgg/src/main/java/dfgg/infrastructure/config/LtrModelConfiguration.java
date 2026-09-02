package dfgg.infrastructure.config;

import dfgg.application.recommend.v3.feature.FeatureExtractionPipeline;
import dfgg.application.recommend.v3.ranker.CandidateRanker;
import dfgg.application.recommend.v3.ranker.GradientBoostedTrees;
import dfgg.application.recommend.v3.ranker.LambdaMartRanker;
import dfgg.application.recommend.v3.ranker.LightGbmModelLoader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 학습된 LTR 모델을 기동 시점에 읽는다.
 * <p>
 * 일부러 지연 로딩하지 않는다.
 * feature 스키마가 어긋난 모델은 예외 없이 다른 feature를 읽으며 그럴듯한 점수를 내기 때문에,
 * 첫 요청이 아니라 기동에서 터뜨린다.
 * 잘못된 추천을 조용히 계속 내보내는 것보다 뜨지 않는 편이 낫다.
 */
@Configuration
public class LtrModelConfiguration {

    @Bean
    public GradientBoostedTrees ltrModel(
            @Value("${recommendation.ltr.model-path:ltr/model.json}") String modelPath) {
        return LightGbmModelLoader.loadFromClasspath(modelPath);
    }

    @Bean
    public CandidateRanker candidateRanker(
            FeatureExtractionPipeline pipeline, GradientBoostedTrees ltrModel) {
        return new LambdaMartRanker(pipeline, ltrModel);
    }
}
