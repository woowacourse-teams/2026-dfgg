package dfgg.domain.match;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 기존 레시피의 수량 배정 정책으로 한 목표의 남은 비용과 재료 충족을 판단한다. */
public final class TargetItemProgress {
    private final PurchaseItemCatalog catalog;
    private final PurchaseRecipeMatcher recipes;

    public TargetItemProgress(PurchaseItemCatalog catalog) {
        this.catalog = catalog;
        this.recipes = new PurchaseRecipeMatcher(catalog);
    }

    public Progress measure(int targetItemId, ItemInventory inventory) {
        // 무관한 보유품의 누락도 조용히 무시하지 않는다.
        catalog.occupiedSlots(inventory);
        PurchaseCatalogItem target = requireItem(targetItemId);
        if (!target.purchasable() || target.totalPrice() <= 0) {
            throw new IllegalStateException("UNSUPPORTED_TARGET_MECHANIC: " + targetItemId);
        }
        int totalLeaves = leafCount(target, Set.of());
        List<PurchaseCatalogItem> missing = recipes.remainingPath(target, inventory);
        if (missing.isEmpty()) {
            return new Progress(0, 0, 1, 1);
        }
        if (missing.stream().anyMatch(item -> !item.purchasable())) {
            throw new IllegalStateException("UNSUPPORTED_TARGET_MECHANIC: 미보유 재료의 획득 조건을 확인할 수 없습니다.");
        }
        long materialPrice = recipes.materialsFor(target, inventory).totalPrice(catalog).orElseThrow();
        long remainingGold = target.totalPrice() - materialPrice;
        if (remainingGold < 0) {
            throw new IllegalStateException("목표 재료 가격이 총가격을 초과합니다.");
        }
        long missingLeaves = missing.stream().filter(item -> item.ingredients().isEmpty()).count();
        double coverage = 1.0 - (double) missingLeaves / totalLeaves;
        double progress = Math.clamp(1.0 - (double) remainingGold / target.totalPrice(), 0.0, 1.0);
        // 기존 구매 정책은 목표를 한 번에 조합 구매할 수 있다. leaf 구매는 조합 연산이 아니다.
        int combinations = target.ingredients().isEmpty() ? 0 : 1;
        return new Progress(remainingGold, combinations, coverage, progress);
    }

    private int leafCount(PurchaseCatalogItem item, Set<Integer> path) {
        if (path.contains(item.id())) throw new IllegalStateException("순환 레시피: " + item.id());
        if (item.ingredients().isEmpty()) return 1;
        Set<Integer> next = new HashSet<>(path);
        next.add(item.id());
        int leaves = 0;
        long ingredientPrice = 0;
        for (int id : item.ingredients()) {
            var child = requireItem(id);
            leaves = Math.addExact(leaves, leafCount(child, next));
            ingredientPrice += child.totalPrice();
        }
        if (ingredientPrice > item.totalPrice()) throw new IllegalStateException("레시피 재료 가격이 총가격을 초과합니다.");
        return leaves;
    }

    private PurchaseCatalogItem requireItem(int id) {
        return catalog.find(id).orElseThrow(() -> new IllegalStateException("UNSUPPORTED_CATALOG_ITEM: " + id));
    }

    public record Progress(long remainingGold, int remainingRecipeDepth, double materialCoverage, double fraction) {}
}
