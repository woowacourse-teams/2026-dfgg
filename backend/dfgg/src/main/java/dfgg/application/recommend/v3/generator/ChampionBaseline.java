package dfgg.application.recommend.v3.generator;

import java.util.Map;

/**
 * lift의 분모 — 이 챔피언이 각 아이템을 산 판 수와 치른 판 수. 전체·최근 두 창을 함께 든다.
 * <p>
 * 한 번도 안 산 아이템은 0이다. 결측이 아니라 "0번 샀다"는 관측이다
 */
public record ChampionBaseline(
        Map<Long, Integer> purchaseCountAllByItem,
        Map<Long, Integer> purchaseCountRecentByItem,
        int gameCountAll,
        int gameCountRecent
) {

    public ChampionBaseline {
        purchaseCountAllByItem = Map.copyOf(purchaseCountAllByItem);
        purchaseCountRecentByItem = Map.copyOf(purchaseCountRecentByItem);
    }

    public int purchaseCountAll(long itemId) {
        return purchaseCountAllByItem.getOrDefault(itemId, 0);
    }

    public int purchaseCountRecent(long itemId) {
        return purchaseCountRecentByItem.getOrDefault(itemId, 0);
    }

    /** 이 챔피언을 한 판이라도 관측했는가. 아니면 분모가 없어 lift를 말할 수 없다. */
    public boolean isObserved() {
        return gameCountAll > 0;
    }
}
