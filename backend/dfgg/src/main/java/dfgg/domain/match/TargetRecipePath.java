package dfgg.domain.match;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** 예산·슬롯·보유 수량과 독립적인 목표의 전체 조합 경로. 구매 가능 여부를 판단하지 않는다. */
public final class TargetRecipePath {
    private final PurchaseItemCatalog catalog;
    private final PurchaseRecipeMatcher recipes;
    private final PurchaseCatalogItem target;
    private final List<Integer> itemIds;

    public TargetRecipePath(int targetItemId, PurchaseItemCatalog catalog) {
        this.catalog = catalog;
        this.recipes = new PurchaseRecipeMatcher(catalog);
        this.target = require(targetItemId);
        this.itemIds = recipes.remainingPath(target, ItemInventory.empty()).stream()
                .map(PurchaseCatalogItem::id).distinct().sorted().toList();
    }

    /** 표준 레시피 ID와 기존 Matcher가 허용하는 동등 변형 재료를 포함한다. */
    public boolean contains(int itemId) {
        require(itemId);
        if (itemIds.contains(itemId)) return true;
        return recipes.materialsFor(target, ItemInventory.empty().add(itemId)).ids().contains(itemId);
    }

    /** 경로 안 재료의 초과 수량은 경로 밖 구매로 분류하지 않는다. */
    public Map<Integer, Integer> outsideItems(Map<Integer, Integer> observedItems) {
        Map<Integer, Integer> outside = new TreeMap<>();
        observedItems.forEach((id, quantity) -> {
            if (quantity == null || quantity <= 0) throw new IllegalArgumentException("구매 수량은 양수여야 합니다.");
            if (!contains(id)) outside.put(id, quantity);
        });
        return Map.copyOf(outside);
    }

    public List<Integer> itemIds() {
        return itemIds;
    }

    private PurchaseCatalogItem require(int id) {
        return catalog.find(id).orElseThrow(() -> new IllegalStateException("UNSUPPORTED_CATALOG_ITEM: " + id));
    }
}
