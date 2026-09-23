package dfgg.domain.champion;

public enum ChampionPosition {
    TOP(6),
    JUNGLE(6),
    MID(6),
    BOTTOM(7),
    SUPPORT(6),
    ;

    /** 보유할 수 있는 코어 아이템 수. BOTTOM(원딜)만 7개다. */
    private final int maximumPurchasedItemCount;

    ChampionPosition(int maximumPurchasedItemCount) {
        this.maximumPurchasedItemCount = maximumPurchasedItemCount;
    }

    /** 상한까지는 받는다 — BOTTOM은 7개까지, 그 외는 6개까지. 넘으면 잘못된 요청이다. */
    public boolean allowsPurchasedItemCount(int purchasedItemCount) {
        return purchasedItemCount <= maximumPurchasedItemCount;
    }

    /** 상한에 닿으면 풀템이다 — 더 살 자리가 없어 추천할 다음 아이템이 없다. */
    public boolean isFullBuild(int purchasedItemCount) {
        return purchasedItemCount >= maximumPurchasedItemCount;
    }
}
