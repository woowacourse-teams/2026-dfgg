package dfgg.application.recommend.v3.generator.transition;

import dfgg.application.recommend.v3.RecommendationQuery;
import dfgg.domain.champion.ChampionPosition;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Build 전이 조회에 들어가는 입력만 모은 것. 아군, 적, 티어, 골드는 이 조회 결과를 바꾸지 않는다.
 */
public record BuildTransitionRequest(
        long championId,
        ChampionPosition position,
        List<Long> purchasedItemIds,
        Set<Long> coreItemIds
) {

    public BuildTransitionRequest {
        purchasedItemIds = List.copyOf(purchasedItemIds);
        coreItemIds = Set.copyOf(coreItemIds);
    }

    public static BuildTransitionRequest from(RecommendationQuery query) {
        return new BuildTransitionRequest(
                query.myChampionId(), query.position(), query.purchasedItemIds(), query.coreItemIds());
    }

    public boolean hasPurchasedItem() {
        return !purchasedItemIds.isEmpty();
    }

    public long lastItemId() {
        return purchasedItemIds.getLast();
    }

    /** 다음에 살 아이템이 구매 순서에서 몇 번째인지(1부터). */
    public int nextPosition() {
        return purchasedItemIds.size() + 1;
    }

    /** 이 요청 뒤에 {@code itemId}를 하나 더 산 요청. 워밍업이 다음 단계 키를 만들 때 쓴다. */
    public BuildTransitionRequest followedBy(long itemId) {
        List<Long> next = new ArrayList<>(purchasedItemIds);
        next.add(itemId);
        return new BuildTransitionRequest(championId, position, next, coreItemIds);
    }

    /** 풀템이면 더 살 자리가 없어 다음 아이템을 묻지 않는다. */
    public boolean isFullBuild() {
        return position.isFullBuild(purchasedItemIds.size());
    }
}
