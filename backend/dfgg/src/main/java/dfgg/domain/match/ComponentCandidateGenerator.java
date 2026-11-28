package dfgg.domain.match;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 목표 경로와 Prepared 후보를 동일 구매 계산기로 생성하고 수량 기준으로 합친다. */
public final class ComponentCandidateGenerator {
    private final PurchaseItemCatalog catalog;
    private final BuildPathResolver resolver;

    public ComponentCandidateGenerator(PurchaseItemCatalog catalog) {
        this.catalog = catalog;
        this.resolver = new BuildPathResolver(catalog);
    }

    /** policies는 ComponentPolicyLookup이 현재 챔피언·포지션으로 고른 근거다. */
    public List<Candidate> generate(List<ComponentPolicyEvidence> policies, int target, ItemInventory inventory, long gold) {
        Map<Map<Integer, Integer>, Candidate> candidates = new LinkedHashMap<>();
        for (var bundle : resolver.resolve(target, inventory, gold)) {
            candidates.put(bundle.itemQuantities(), new Candidate(bundle, Source.TARGET_PATH, null));
        }
        if (new TargetItemProgress(catalog).measure(target, inventory).remainingGold() == 0) return List.copyOf(candidates.values());
        for (var policy : policies) {
            int item = policy.itemId();
            if (catalog.find(item).isEmpty() || PreparedComponentPolicy.alreadyInLine(item, inventory)) continue;
            try {
                var bundle = resolver.singlePurchase(item, inventory, gold);
                if (bundle == null) continue;
                new TargetItemProgress(catalog).measure(target, bundle.inventoryAfter());
                candidates.putIfAbsent(bundle.itemQuantities(), new Candidate(bundle, Source.PREPARED, policy));
                // 정책 아이템 구매 후 남은 Gold로 목표 재료를 함께 사는 묶음. 방금 산 정책 아이템을 재조합하는 경로는 제외한다.
                for (var path : resolver.resolve(target, bundle.inventoryAfter(), gold - bundle.additionalCost())) {
                    int bought = bundle.inventoryAfter().quantityOf(item) + path.itemQuantities().getOrDefault(item, 0);
                    if (path.inventoryAfter().quantityOf(item) < bought) continue;
                    var combined = combine(bundle, path);
                    candidates.putIfAbsent(combined.itemQuantities(), new Candidate(combined, Source.PREPARED, policy));
                }
            } catch (IllegalArgumentException | IllegalStateException unsupportedPolicyItem) {
                // 지원하지 않는 정책 아이템 하나가 기존 목표 경로 후보를 제거하지 않는다.
            }
        }
        return List.copyOf(candidates.values());
    }

    private static BuildPathResolver.PurchaseBundle combine(BuildPathResolver.PurchaseBundle first,
            BuildPathResolver.PurchaseBundle second) {
        var quantities = new java.util.HashMap<>(first.itemQuantities());
        second.itemQuantities().forEach((id, quantity) -> quantities.merge(id, quantity, Integer::sum));
        var order = new java.util.ArrayList<>(first.purchaseOrder());
        order.addAll(second.purchaseOrder());
        return new BuildPathResolver.PurchaseBundle(quantities, first.additionalCost() + second.additionalCost(),
                second.inventoryAfter(), order);
    }

    public enum Source { TARGET_PATH, PREPARED }

    public record Candidate(BuildPathResolver.PurchaseBundle bundle, Source candidateSource,
                            ComponentPolicyEvidence policyEvidence) {
        public Integer policyItemId() { return policyEvidence == null ? null : policyEvidence.itemId(); }
        public List<Double> policyFeatures() {
            return policyEvidence == null ? ComponentPolicyEvidence.NONE : policyEvidence.features();
        }
    }
}
