package dfgg.presentation.dto;

/** v3 응답 계약은 유지하고 v4에서만 구매 묶음을 제공한다. */
public record RecommendedItemV4Dto(
        Long id,
        String name,
        String imageUrl,
        RecommendationDescription description,
        PurchaseBundleDto purchaseBundle
) {
    public static RecommendedItemV4Dto of(RecommendedItemDto core, PurchaseBundleDto purchaseBundle) {
        return new RecommendedItemV4Dto(core.id(), core.name(), core.imageUrl(), core.description(), purchaseBundle);
    }
}
