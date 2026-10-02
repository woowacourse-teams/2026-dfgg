package dfgg.evaluation;

import dfgg.application.recommend.v3.RecommendationQuery;

/**
 * 평가용 게임 스냅샷 하나 — "이 상황에서 실제로 산 다음 아이템은 무엇이었나".
 *
 * @param matchId          분할(train/test)의 단위. 같은 게임의 스냅샷은 함께 움직여야 한다
 * @param purchaseStep     0부터. 코어를 몇 개 산 시점인지
 * @param groundTruthItemId 그 시점에서 실제로 다음에 산 아이템
 */
public record SnapshotQuery(
        String matchId,
        int purchaseStep,
        RecommendationQuery query,
        long groundTruthItemId
) {
}
