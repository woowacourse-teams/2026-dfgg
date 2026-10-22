package dfgg.domain.match;

import java.util.HashSet;
import java.util.Set;

/** 구매 로그 분류와 별개로 Component의 완성 목표 범위 및 특수 획득 조건을 판단한다. */
public final class ComponentTargetPolicy {
    private final PurchaseItemCatalog catalog;

    public ComponentTargetPolicy(PurchaseItemCatalog catalog) {
        this.catalog = catalog;
    }

    public boolean isObservedTarget(int id, ItemPurchaseType purchaseType) {
        if (purchaseType == ItemPurchaseType.CORE) return true;
        return purchaseType == ItemPurchaseType.BOOTS && isTierTwoBoots(require(id));
    }

    public Kind kind(int id) {
        var target = require(id);
        if (target.boots()) {
            if (!isTierTwoBoots(target)) throw new IllegalStateException("UNSUPPORTED_TARGET_TYPE: " + id);
            return Kind.TIER_TWO_BOOTS;
        }
        if (target.ingredients().isEmpty()) throw new IllegalStateException("UNSUPPORTED_TARGET_TYPE: " + id);
        // 현재 패치 레시피가 구매 불가 전구체를 요구할 때만 특수 획득으로 분류한다.
        if (hasNonPurchasableLeaf(target, Set.of())) {
            if (target.ingredients().contains(3867)) return Kind.SUPPORT_FINISHED;
            throw new IllegalStateException("UNSUPPORTED_TARGET_MECHANIC: 미확인 특수 획득 목표 " + id);
        }
        return Kind.CORE;
    }

    public void validate(int id, ItemInventory inventory) {
        kind(id);
        new TargetItemProgress(catalog).measure(id, inventory);
    }

    private boolean isTierTwoBoots(PurchaseCatalogItem target) {
        return target.purchasable() && target.ingredients().stream().map(this::require)
                .anyMatch(child -> child.boots() && child.ingredients().isEmpty());
    }

    private boolean hasNonPurchasableLeaf(PurchaseCatalogItem item, Set<Integer> path) {
        if (path.contains(item.id())) throw new IllegalStateException("순환 레시피: " + item.id());
        if (item.ingredients().isEmpty()) return !item.purchasable();
        var next = new HashSet<>(path);
        next.add(item.id());
        return item.ingredients().stream().map(this::require).anyMatch(child -> hasNonPurchasableLeaf(child, next));
    }

    private PurchaseCatalogItem require(int id) {
        return catalog.find(id).orElseThrow(() -> new IllegalStateException("UNSUPPORTED_CATALOG_ITEM: " + id));
    }

    public enum Kind { CORE, TIER_TWO_BOOTS, SUPPORT_FINISHED }
}
