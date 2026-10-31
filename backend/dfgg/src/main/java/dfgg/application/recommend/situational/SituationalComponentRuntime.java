package dfgg.application.recommend.situational;

import dfgg.domain.match.BuildPathResolver;
import dfgg.domain.match.ItemInventory;
import dfgg.domain.match.SituationalComponentLookup;
import dfgg.domain.match.SituationalComponentPolicy;
import dfgg.domain.match.SituationalThresholds;
import dfgg.infrastructure.external.catalog.DataDragonPurchaseItemCatalog;
import dfgg.infrastructure.external.dto.ItemResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** 조회표 근거에 보유 제외·효과당 하나 선택·구매 비용을 더해 상황 대응 추천을 만든다. 준비 실패 시 빈 목록을 낸다. */
public final class SituationalComponentRuntime {
    private final SituationalComponentLookup lookup;
    private final SituationalThresholds thresholds;

    private SituationalComponentRuntime(SituationalComponentLookup lookup, SituationalThresholds thresholds) {
        this.lookup = lookup;
        this.thresholds = thresholds;
    }

    public static SituationalComponentRuntime disabled() {
        return new SituationalComponentRuntime(null, null);
    }

    public static SituationalComponentRuntime available(SituationalComponentLookup lookup, SituationalThresholds thresholds) {
        return new SituationalComponentRuntime(Objects.requireNonNull(lookup), Objects.requireNonNull(thresholds));
    }

    public boolean isAvailable() {
        return lookup != null;
    }

    public List<SituationalRecommendation> recommend(int championId, String role, Collection<Integer> enemyChampionIds,
            ItemInventory inventory, Collection<Integer> purchasedItemIds, long availableGold, ItemResponse catalogResponse) {
        if (!isAvailable()) return List.of();
        var catalog = new DataDragonPurchaseItemCatalog(catalogResponse.data());
        var owned = new HashSet<>(inventory.quantities().keySet());
        owned.addAll(purchasedItemIds);
        var covered = EnumSet.noneOf(SituationalComponentPolicy.Effect.class);
        var recommendations = new ArrayList<SituationalRecommendation>();
        for (var evidence : lookup.evidenceFor(championId, role, enemyChampionIds, thresholds)) {
            if (covered.contains(evidence.effect())
                    || SituationalComponentPolicy.effectAlreadyOwned(evidence.itemId(), owned, catalog)) continue;
            Long cost = additionalCost(evidence.itemId(), inventory, catalog);
            if (cost == null) continue;
            covered.add(evidence.effect());
            recommendations.add(new SituationalRecommendation(evidence.itemId(), evidence.effect(),
                    evidence.reasonChampionIds(), cost, availableGold >= cost));
        }
        recommendations.sort(java.util.Comparator.comparing(SituationalRecommendation::effect));
        return List.copyOf(recommendations);
    }

    /** 현재 인벤토리에서 한 개 살 때의 추가 비용. 카탈로그 누락·슬롯 초과로 살 수 없으면 null이다. */
    private Long additionalCost(int itemId, ItemInventory inventory, DataDragonPurchaseItemCatalog catalog) {
        if (catalog.find(itemId).isEmpty()) return null;
        try {
            var bundle = new BuildPathResolver(catalog).singlePurchase(itemId, inventory, Long.MAX_VALUE);
            return bundle == null ? null : bundle.additionalCost();
        } catch (IllegalArgumentException | IllegalStateException unsupported) {
            return null;
        }
    }

    public record SituationalRecommendation(int itemId, SituationalComponentPolicy.Effect effect,
                                            List<Integer> reasonChampionIds, long additionalCost, boolean affordable) {
        public SituationalRecommendation {
            reasonChampionIds = List.copyOf(reasonChampionIds);
        }
    }
}
