package dfgg.domain.match;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 목표 경로와 선구매 후보를 동일 구매 계산기로 생성하고 수량 기준으로 합친다. */
public final class ComponentCandidateGenerator {
    private final PurchaseItemCatalog catalog;
    private final PreparationLookup lookup;
    private final BuildPathResolver resolver;

    public ComponentCandidateGenerator(PurchaseItemCatalog catalog, PreparationLookup lookup) {
        this.catalog = catalog;
        this.lookup = lookup;
        this.resolver = new BuildPathResolver(catalog);
    }

    public List<Candidate> generate(Integer championId, String role, String patch, int target,
            ItemInventory inventory, long gold) {
        lookup.validatePatch(patch);
        var patterns = lookup.find(championId, role, target);
        Map<Map<Integer, Integer>, Candidate> candidates = new LinkedHashMap<>();
        for (var bundle : resolver.resolve(target, inventory, gold)) {
            candidates.put(bundle.itemQuantities(), new Candidate(bundle, Source.TARGET_PATH, null));
        }
        if (new TargetItemProgress(catalog).measure(target, inventory).remainingGold() == 0) return List.copyOf(candidates.values());
        for (var pattern : patterns) {
            if (catalog.find(pattern.itemId()).isEmpty() || inventory.quantityOf(pattern.itemId()) > 0) continue;
            try {
                var bundle = resolver.singlePurchase(pattern.itemId(), inventory, gold);
                if (bundle == null) continue;
                new TargetItemProgress(catalog).measure(target, bundle.inventoryAfter());
                candidates.putIfAbsent(bundle.itemQuantities(), new Candidate(bundle, Source.PREPARATION,
                        pattern));
            } catch (IllegalArgumentException | IllegalStateException unsupportedPreparation) {
                // 지원하지 않는 선구매 레시피 하나가 기존 목표 경로 후보를 제거하지 않는다.
            }
        }
        return List.copyOf(candidates.values());
    }
    public enum Source { TARGET_PATH, PREPARATION }
    public record Candidate(BuildPathResolver.PurchaseBundle bundle, Source candidateSource,
                            PreparationLookup.Pattern preparationPattern) {
        public Integer preparationPatternCount() { return preparationPattern == null ? null : preparationPattern.count(); }
        public Double preparationFutureRecipeLinkRate() { return preparationPattern == null ? null : preparationPattern.futureRecipeLinkRate(); }
        public List<Double> preparationFeatures() {
            if (candidateSource == Source.TARGET_PATH) return List.of(0.0, 0.0, 0.0, 0.0);
            return java.util.Objects.requireNonNull(preparationPattern).rankingFeatures();
        }
    }
}
