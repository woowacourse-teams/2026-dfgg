package dfgg.domain.champion;

public enum ChampionPosition {
    TOP(6),
    JUNGLE(6),
    MID(6),
    BOTTOM(7),
    SUPPORT(6),
    ;

    /** 구매 아이템 수의 상한. BOTTOM(원딜)만 7개다. */
    private final int maximumPurchasedItemCount;

    ChampionPosition(int maximumPurchasedItemCount) {
        this.maximumPurchasedItemCount = maximumPurchasedItemCount;
    }

    /** 상한은 포함이다 — BOTTOM은 7개까지, 그 외는 6개까지 받는다. */
    public boolean allowsPurchasedItemCount(int purchasedItemCount) {
        return purchasedItemCount <= maximumPurchasedItemCount;
    }
}
