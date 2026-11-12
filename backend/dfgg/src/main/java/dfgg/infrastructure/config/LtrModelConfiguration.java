package dfgg.infrastructure.config;

import dfgg.application.recommend.v3.feature.FeatureExtractionPipeline;
import dfgg.application.recommend.v3.explanation.ChampionDirectory;
import dfgg.application.recommend.v3.feature.FeatureName;
import dfgg.application.recommend.v3.ranker.CandidateRanker;
import dfgg.application.recommend.v3.ranker.GradientBoostedTrees;
import dfgg.application.recommend.v3.ranker.LambdaMartRanker;
import dfgg.application.recommend.v3.ranker.LightGbmModelLoader;
import dfgg.application.recommend.v3.ranker.TreeShapCalculator;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

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
            @Value("${recommendation.ltr.model-path:ltr/model.json}") String modelPath,
            ResourceLoader resources) {
        // 기존 접두사 없는 경로는 classpath로 유지하고 EC2의 file:/...도 허용한다.
        var resource = resources.getResource(modelPath.contains(":") ? modelPath : "classpath:" + modelPath);
        try (var input = resource.getInputStream()) {
            return LightGbmModelLoader.load(input);
        } catch (IOException exception) {
            throw new UncheckedIOException("Core LTR 모델을 읽지 못했습니다: " + modelPath, exception);
        }
    }

    /**
     * 추천 이유 계산기. 모델과 feature 개수가 고정이라 요청마다 새로 만들 이유가 없다.
     * 트리 깊이만큼의 경로 배열은 계산할 때마다 새로 잡으므로 상태를 공유하지 않는다.
     */
    @Bean
    public TreeShapCalculator treeShapCalculator(@Qualifier("ltrModel") GradientBoostedTrees ltrModel) {
        return new TreeShapCalculator(ltrModel, FeatureName.values().length);
    }


    @Bean
    public ChampionDirectory championDirectory(dfgg.domain.champion.ChampionRepository championRepository) {
        return new ChampionDirectory(championRepository);
    }

    @Bean
    public dfgg.domain.item.trait.ItemTraitCatalog itemTraitCatalog() {
        return new dfgg.domain.item.trait.ItemTraitCatalog();
    }

    @Bean
    public CandidateRanker candidateRanker(
            FeatureExtractionPipeline pipeline, @Qualifier("ltrModel") GradientBoostedTrees ltrModel) {
        return new LambdaMartRanker(pipeline, ltrModel);
    }
}
