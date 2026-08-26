package dfgg.application.recommend.fallback;

import dfgg.domain.champion.ChampionPosition;
import java.util.List;

/**
 * 폴백 체인의 모든 단계가 공유하는 추천 요청 정보.
 * 단계마다 필요한 필드가 다르므로(예: 최다빈도 빌드는 챔피언·포지션만 사용) 각 단계가
 * 필요한 것만 골라 쓴다.
 */
public record RecommendationContext(
        Long myChampionId,
        List<Long> purchasedItemIds,
        ChampionPosition position,
        String tier,
        String patch,
        List<Long> allyChampionIds,
        List<Long> enemyChampionIds
) {

}
