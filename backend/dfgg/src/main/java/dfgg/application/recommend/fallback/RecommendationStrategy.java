package dfgg.application.recommend.fallback;

import java.util.List;
import java.util.Optional;

/**
 * 폴백 체인의 한 단계. 자기 단계에서 추천을 만들지 못하면 빈 Optional을 반환해
 * 체인이 다음 단계로 내려가게 한다.
 */
public interface RecommendationStrategy {

    FallbackStage stage();

    Optional<List<Long>> recommend(RecommendationContext context);
}
