package dfgg.domain.match;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 목표 Core의 남은 경로에서 예산·일반 6슬롯을 만족하는 구매 묶음을 만든다. */
public final class BuildPathResolver {
    private static final int INVENTORY_SLOTS = 6;
    private final PurchaseItemCatalog catalog;
    private final PurchaseRecipeMatcher recipes;

    public BuildPathResolver(PurchaseItemCatalog catalog) {
        if (catalog == null) {
            throw new IllegalArgumentException("패치별 카탈로그가 필요합니다.");
        }
        this.catalog = catalog;
        this.recipes = new PurchaseRecipeMatcher(catalog);
    }

    /** Gold Range를 사용할 때는 호출자가 하한을 budget으로 전달한다. */
    public List<PurchaseBundle> resolve(int targetCoreId, ItemInventory inventory, long budget) {
        if (inventory == null || budget < 0) {
            throw new IllegalArgumentException("현재 Inventory와 음수가 아닌 예산이 필요합니다.");
        }
        PurchaseCatalogItem target = catalog.find(targetCoreId)
                .orElseThrow(() -> new IllegalArgumentException("목표 Core가 카탈로그에 없습니다."));
        if (!target.purchasable()) {
            throw new IllegalArgumentException("구매 가능한 목표 Core가 필요합니다.");
        }
        if (catalog.occupiedSlots(inventory) > INVENTORY_SLOTS) {
            throw new IllegalArgumentException("일반 인벤토리가 6슬롯을 초과합니다.");
        }
        ArrayDeque<PurchaseBundle> pending = new ArrayDeque<>();
        pending.add(new PurchaseBundle(Map.of(), 0, inventory, List.of()));
        Map<SearchState, Long> visited = new HashMap<>();
        Map<Map<Integer, Integer>, PurchaseBundle> bundles = new LinkedHashMap<>();
        while (!pending.isEmpty()) {
            PurchaseBundle current = pending.removeFirst();
            var candidates = recipes.remainingPath(target, current.inventoryAfter()).stream()
                    .filter(PurchaseCatalogItem::purchasable)
                    .distinct().sorted(Comparator.comparingInt(PurchaseCatalogItem::id)).toList();
            for (PurchaseCatalogItem candidate : candidates) {
                PurchaseBundle next = purchase(current, candidate, budget);
                if (next == null || !visit(next, visited)) {
                    continue;
                }
                bundles.merge(next.itemQuantities(), next, (first, second) ->
                        first.additionalCost() <= second.additionalCost() ? first : second);
                pending.addLast(next);
            }
        }
        return List.copyOf(bundles.values());
    }

    private PurchaseBundle purchase(PurchaseBundle current, PurchaseCatalogItem item, long budget) {
        PurchaseMaterials materials = recipes.materialsFor(item, current.inventoryAfter());
        if (materials.ids().stream().anyMatch(current.itemQuantities()::containsKey)) {
            // 관측 정답과 동일하게, 묶음 안에서 방금 구매한 재료의 재조합은 첫 버전에서 제외한다.
            return null;
        }
        int materialPrice = materials.totalPrice(catalog)
                .orElseThrow(() -> new IllegalStateException("조합 재료의 가격을 계산할 수 없습니다."));
        long additionalCost = (long) item.totalPrice() - materialPrice;
        if (additionalCost < 0) {
            throw new IllegalStateException("조합 재료의 가격이 완성품 총가격을 초과합니다.");
        }
        if (additionalCost > budget - current.additionalCost()) {
            return null;
        }
        ItemInventory after = current.inventoryAfter();
        for (int materialId : materials.ids()) {
            after = after.remove(materialId).orElseThrow();
        }
        after = after.add(item.id());
        if (catalog.occupiedSlots(after) > INVENTORY_SLOTS) {
            return null;
        }
        Map<Integer, Integer> quantities = new HashMap<>(current.itemQuantities());
        quantities.merge(item.id(), 1, Integer::sum);
        List<Integer> order = new ArrayList<>(current.purchaseOrder());
        order.add(item.id());
        return new PurchaseBundle(quantities, current.additionalCost() + additionalCost, after, order);
    }

    private boolean visit(PurchaseBundle bundle, Map<SearchState, Long> visited) {
        SearchState state = new SearchState(bundle.itemQuantities(), bundle.inventoryAfter());
        Long previousCost = visited.get(state);
        if (previousCost != null && previousCost <= bundle.additionalCost()) {
            return false;
        }
        visited.put(state, bundle.additionalCost());
        return true;
    }

    /** 같은 묶음은 아이템 수량으로 식별한다. purchaseOrder는 실제 실행 가능한 순서의 예시다. */
    public record PurchaseBundle(Map<Integer, Integer> itemQuantities, long additionalCost,
                                 ItemInventory inventoryAfter, List<Integer> purchaseOrder) {
        public PurchaseBundle {
            if (itemQuantities == null || inventoryAfter == null || purchaseOrder == null || additionalCost < 0
                    || itemQuantities.entrySet().stream().anyMatch(entry -> entry.getKey() <= 0 || entry.getValue() <= 0)) {
                throw new IllegalArgumentException("구매 묶음의 아이템·비용·Inventory가 유효해야 합니다.");
            }
            itemQuantities = Map.copyOf(itemQuantities);
            purchaseOrder = List.copyOf(purchaseOrder);
        }
    }

    private record SearchState(Map<Integer, Integer> quantities, ItemInventory inventory) {
    }
}
