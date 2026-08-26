package dfgg.application.recommend.fallback;

import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 등록된 순서대로 추천 전략을 시도하고, 처음으로 결과를 낸 단계의 추천을 반환한다.
 * 앞 단계가 성공하면 뒷 단계는 실행하지 않는다.
 */
public class FallbackChain {

    private static final Logger log = LoggerFactory.getLogger(FallbackChain.class);

    private final List<RecommendationStrategy> strategies;

    public FallbackChain(List<RecommendationStrategy> strategies) {
        this.strategies = List.copyOf(strategies);
    }

    public Optional<FallbackRecommendation> recommend(RecommendationContext context) {
        for (RecommendationStrategy strategy : strategies) {
            Optional<List<Long>> itemIds = strategy.recommend(context);
            if (itemIds.isPresent() && !itemIds.get().isEmpty()) {
                log.debug(
                        "Recommendation served: championId={}, position={}, stage={}",
                        context.myChampionId(), context.position(), strategy.stage()
                );
                return Optional.of(new FallbackRecommendation(itemIds.get(), strategy.stage()));
            }
        }
        log.warn(
                "No recommendation from any fallback stage: championId={}, position={}, tier={}, patch={}",
                context.myChampionId(), context.position(), context.tier(), context.patch()
        );
        return Optional.empty();
    }
}
