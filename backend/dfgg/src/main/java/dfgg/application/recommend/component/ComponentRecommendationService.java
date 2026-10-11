package dfgg.application.recommend.component;

import dfgg.application.recommend.v3.ranker.GradientBoostedTrees;
import dfgg.domain.match.ComponentCandidateGenerator;
import dfgg.domain.match.PreparationLookup;
import dfgg.application.ChampionPositionNormalizer;
import dfgg.domain.match.ComponentTargetPolicy;
import dfgg.domain.match.ItemInventory;
import dfgg.infrastructure.external.catalog.DataDragonPurchaseItemCatalog;
import dfgg.infrastructure.external.dto.ItemResponse;
import java.util.List;
import dfgg.application.recommend.context.CompositionFeatures;

/** Core 순서를 유지하며 (현재 상태, 목표 하나)마다 독립적으로 후보를 생성하고 순위를 매긴다. */
public final class ComponentRecommendationService {
    private final ComponentRanker ranker;
    private final PreparationLookup lookup;

    public ComponentRecommendationService(GradientBoostedTrees model) {
        this(model, PreparationLookup.disabled());
    }

    public ComponentRecommendationService(GradientBoostedTrees model, PreparationLookup lookup) {
        this(new ComponentRanker(model), lookup);
    }

    public ComponentRecommendationService(ComponentRanker ranker, PreparationLookup lookup) {
        this.lookup = lookup;
        this.ranker = ranker;
    }

    public List<TargetRecommendation> recommend(List<Integer> targetItemIds, ItemInventory inventory,
            long availableGold, long gameTimeMs, String position, String patch, ItemResponse response, CompositionFeatures composition) {
        return recommend(null, targetItemIds, inventory, availableGold, gameTimeMs, position, patch, response, composition);
    }

    public List<TargetRecommendation> recommend(Integer championId, List<Integer> targetItemIds, ItemInventory inventory,
            long availableGold, long gameTimeMs, String position, String patch, ItemResponse response, CompositionFeatures composition) {
        var normalizedPosition = new ChampionPositionNormalizer().normalize(position).orElse(null);
        var query = new ComponentRecommendationQuery(championId, targetItemIds, inventory, availableGold,
                gameTimeMs, normalizedPosition, patch, composition);
        return recommend(query, response);
    }

    public List<TargetRecommendation> recommend(ComponentRecommendationQuery query, ItemResponse response) {
        lookup.validatePatch(query.patch());
        if (lookup.enabled() && query.championId() == null) throw new IllegalArgumentException("Preparation 추천에는 Champion ID가 필요합니다.");
        String version = response.dataVersion() == null ? response.version() : response.dataVersion();
        if (version == null || !version.matches("\\d+\\.\\d+\\.\\d+")
                || !version.substring(0, version.lastIndexOf('.')).equals(query.patch())) {
            throw new IllegalArgumentException("요청과 아이템 카탈로그의 패치가 다릅니다.");
        }
        return query.targetItemIds().stream().map(id -> recommendTarget(id, query, response)).toList();
    }

    private TargetRecommendation recommendTarget(int id, ComponentRecommendationQuery query, ItemResponse response) {
        var catalog = new DataDragonPurchaseItemCatalog(response.data());
        if (catalog.find(id).isEmpty()) return failure(id, Status.UNSUPPORTED_CATALOG_ITEM, "목표가 패치 카탈로그에 없습니다: " + id);
        try {
            new ComponentTargetPolicy(catalog).validate(id, query.inventory());
            var candidates = new ComponentCandidateGenerator(catalog, lookup).generate(query.championId(),
                    query.positionName(), query.patch(), id, query.inventory(), query.availableGold());
            if (candidates.isEmpty()) {
                return failure(id, Status.NO_CANDIDATE, "목표 보유 또는 예산/슬롯 조건을 만족하는 후보가 없습니다.");
            }
            var ranked = ranker.rank(candidates, query.inventory(), id, catalog, response,
                    query.gameTimeMs(), query.positionName(), query.composition());
            return new TargetRecommendation(id, Status.OK, ranked, null);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            String reason = exception.getMessage();
            Status status = reason != null && reason.startsWith("UNSUPPORTED_TARGET_MECHANIC") ? Status.UNSUPPORTED_TARGET_MECHANIC
                    : reason != null && reason.startsWith("UNSUPPORTED_CATALOG_ITEM") ? Status.UNSUPPORTED_CATALOG_ITEM
                    : reason != null && reason.startsWith("UNSUPPORTED_TARGET_TYPE") ? Status.UNSUPPORTED_TARGET_TYPE
                    : Status.CALCULATION_FAILED;
            return failure(id, status, reason);
        }
    }

    private TargetRecommendation failure(int id, Status status, String reason) {
        return new TargetRecommendation(id, status, List.of(), reason);
    }

    public enum Status { OK, NO_CANDIDATE, UNSUPPORTED_CATALOG_ITEM, UNSUPPORTED_TARGET_MECHANIC, UNSUPPORTED_TARGET_TYPE, CALCULATION_FAILED }
    public record TargetRecommendation(int targetItemId, Status status, List<ScoredPurchase> rankedPurchases, String reason) {}
}
