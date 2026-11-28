package dfgg.application.recommend.v3.generator.transition;

import dfgg.domain.champion.ChampionPosition;
import java.util.List;

/**
 * Build 전이 캐시 키. 데이터 버전은 키마다 들고 다니지 않고 세대 번호로 넣는다.
 */
record BuildTransitionKey(long generation, Kind kind, long championId, ChampionPosition position, List<Long> items) {

    enum Kind {
        EXACT_PREFIX,
        LAST_ITEM
    }

    BuildTransitionKey {
        items = List.copyOf(items);
    }

    static BuildTransitionKey exactPrefix(long generation, BuildTransitionRequest request) {
        return new BuildTransitionKey(generation, Kind.EXACT_PREFIX, request.championId(), request.position(),
                request.purchasedItemIds());
    }

    static BuildTransitionKey lastItem(long generation, BuildTransitionRequest request) {
        return new BuildTransitionKey(generation, Kind.LAST_ITEM, request.championId(), request.position(),
                List.of(request.lastItemId()));
    }
}
