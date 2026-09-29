package dfgg.application.match;

import dfgg.domain.match.PurchaseCatalogItem;
import dfgg.domain.match.PurchaseMaterials;
import dfgg.domain.match.PurchaseRecipeMatcher;
import dfgg.infrastructure.external.catalog.DataDragonPurchaseItemCatalog;
import dfgg.infrastructure.external.dto.ItemData;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * 한 번의 ITEM_PURCHASED에서 추가로 지출한 골드를 패치 카탈로그와 관측 재료로 추정한다.
 * 호출자가 구매에 연결된 ITEM_DESTROYED 목록과 해당 경기의 Data Dragon 빌드 카탈로그를 준비해야 한다.
 * 이 클래스는 카탈로그 변환, 레시피 대조, 재료 가격 합산을 조율하며 이벤트 연결이나 Gold Range 계산은 맡지 않는다.
 * 결과는 카탈로그 기반 비용 가설이고 게임이 실제로 청구한 금액을 직접 관측한 값은 아니다.
 */
public class PurchaseCostEstimator {

    // 할인 규칙이 별도로 검증되지 않은 구매는 카탈로그 정가로 확정하지 않는다.
    private static final int CONTROL_WARD = 2055;

    /**
     * 관측된 제거 재료가 구매 아이템의 조합 경로에 대응하면 총가격에서 그 재료들의 총가격을 뺀다.
     * 카탈로그 정보 부족, 제외 대상, 레시피 불일치, 양수가 아닌 계산 결과는 빈 값으로 돌려준다.
     *
     * @param purchasedItemId ITEM_PURCHASED에 기록된 아이템 ID
     * @param destroyedItemIds 해당 구매에 연결된 ITEM_DESTROYED의 아이템 ID 목록. 중복 수량을 유지한다.
     * @param catalog 해당 경기의 Data Dragon 빌드에서 조회한 전체 아이템 데이터
     * @return 계산 가능한 양수 지출액 또는 빈 값
     */
    public OptionalInt estimate(int purchasedItemId, List<Integer> destroyedItemIds, Map<String, ItemData> catalog) {
        if (purchasedItemId <= 0) {
            throw new IllegalArgumentException("구매 아이템 ID는 양수여야 합니다.");
        }
        DataDragonPurchaseItemCatalog items = new DataDragonPurchaseItemCatalog(catalog);
        PurchaseMaterials materials = new PurchaseMaterials(destroyedItemIds);
        Optional<PurchaseCatalogItem> found = items.find(purchasedItemId);
        if (found.isEmpty()) {
            return OptionalInt.empty();
        }
        PurchaseCatalogItem purchased = found.orElseThrow();
        if (!canEstimate(purchased)) {
            return OptionalInt.empty();
        }
        if (!new PurchaseRecipeMatcher(items).matches(purchased, materials)) {
            return OptionalInt.empty();
        }
        return additionalCost(purchased, materials, items);
    }

    /** 구매 불가 아이템과 할인·특수 가격 검증이 필요한 구매를 일반 가격 계산에서 제외한다. */
    private boolean canEstimate(PurchaseCatalogItem item) {
        if (!item.purchasable() || item.id() >= 1200 && item.id() < 1300) {
            return false;
        }
        return item.id() != CONTROL_WARD;
    }

    /** 재료 가격을 구할 수 있고 차액이 양수일 때만 추가 지출액의 비용 가설을 돌려준다. */
    private OptionalInt additionalCost(
            PurchaseCatalogItem purchased,
            PurchaseMaterials materials,
            DataDragonPurchaseItemCatalog catalog
    ) {
        OptionalInt materialPrice = materials.totalPrice(catalog);
        if (materialPrice.isEmpty()) {
            return OptionalInt.empty();
        }
        int cost = purchased.totalPrice() - materialPrice.getAsInt();
        if (cost <= 0) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(cost);
    }
}
