package dfgg.application.recommend.fallback;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 폴백 체인의 단계 순서를 명시적으로 고정한다.
 * ②(patch/tier 완화)·③(k-NN 유사 상황 검색)는 아직 전략 구현이 없어 목록에서 빠져있다 —
 * 나중에 구현되면 {@link FallbackStage} 순서(PRIMARY → RELAXED_SCOPE → SIMILAR_SITUATION →
 * COMPOSITION_STATS → MOST_FREQUENT_BUILD)에 맞춰 이 목록 중간에 추가하면 된다.
 */
@Configuration
public class FallbackChainConfiguration {

    @Bean
    public FallbackChain fallbackChain(
            PrimaryRecommendationStrategy primaryRecommendationStrategy,
            CompositionStatsStrategy compositionStatsStrategy,
            MostFrequentBuildStrategy mostFrequentBuildStrategy
    ) {
        return new FallbackChain(List.of(
                primaryRecommendationStrategy,
                compositionStatsStrategy,
                mostFrequentBuildStrategy
        ));
    }
}
