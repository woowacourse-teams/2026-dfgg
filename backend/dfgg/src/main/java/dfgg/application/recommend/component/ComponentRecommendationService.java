package dfgg.application.recommend.component;

import dfgg.application.recommend.v3.ranker.GradientBoostedTrees;
import dfgg.domain.match.ComponentCandidateGenerator;
import dfgg.domain.match.PreparationLookup;
import dfgg.application.ChampionPositionNormalizer;
import dfgg.domain.match.ComponentTargetPolicy;
import dfgg.domain.match.ItemInventory;
import dfgg.infrastructure.external.catalog.DataDragonPurchaseItemCatalog;
import dfgg.infrastructure.external.dto.ItemResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import dfgg.application.recommend.context.CompositionFeatures;

/** Core 순서를 유지하며 (현재 상태, 목표 하나)마다 독립적으로 후보를 생성하고 순위를 매긴다. */
public final class ComponentRecommendationService {
    private final GradientBoostedTrees model;
    private final PreparationLookup lookup;

    public ComponentRecommendationService(GradientBoostedTrees model) {
        this(model, PreparationLookup.disabled());
    }

    public ComponentRecommendationService(GradientBoostedTrees model, PreparationLookup lookup) {
        this.lookup = lookup;
        this.model = model;
    }

    public List<TargetRecommendation> recommend(List<Integer> targetItemIds, ItemInventory inventory,
            long availableGold, long gameTimeMs, String position, String patch, ItemResponse response, CompositionFeatures composition) {
        return recommend(null, targetItemIds, inventory, availableGold, gameTimeMs, position, patch, response, composition);
    }

    public List<TargetRecommendation> recommend(Integer championId, List<Integer> targetItemIds, ItemInventory inventory,
            long availableGold, long gameTimeMs, String position, String patch, ItemResponse response, CompositionFeatures composition) {
        java.util.Objects.requireNonNull(composition, "경기 조합은 필수입니다.");
        lookup.validatePatch(patch);
        if (lookup.enabled() && championId == null) throw new IllegalArgumentException("Preparation 추천에는 Champion ID가 필요합니다.");
        String version = response.dataVersion() == null ? response.version() : response.dataVersion();
        if (version == null || !version.matches("\\d+\\.\\d+\\.\\d+")
                || !version.substring(0, version.lastIndexOf('.')).equals(patch)) {
            throw new IllegalArgumentException("요청과 아이템 카탈로그의 패치가 다릅니다.");
        }
        if (inventory == null || availableGold < 0 || gameTimeMs < 0) {
            throw new IllegalArgumentException("현재 Inventory, 예산, 시간이 필요합니다.");
        }
        return targetItemIds.stream().map(id -> recommendTarget(id, inventory, availableGold, gameTimeMs,
                position, patch, response, championId, composition)).toList();
    }

    private TargetRecommendation recommendTarget(int id, ItemInventory inventory, long gold, long time,
            String position, String patch, ItemResponse response, Integer championId, CompositionFeatures composition) {
        var catalog = new DataDragonPurchaseItemCatalog(response.data());
        if (catalog.find(id).isEmpty()) return failure(id, Status.UNSUPPORTED_CATALOG_ITEM, "목표가 패치 카탈로그에 없습니다: " + id);
        try {
            new ComponentTargetPolicy(catalog).validate(id, inventory);
            String role = new ChampionPositionNormalizer().normalize(position).map(Enum::name).orElse("UNKNOWN");
            var candidates = new ComponentCandidateGenerator(catalog, lookup).generate(championId, role, patch, id, inventory, gold);
            if (candidates.isEmpty()) {
                return failure(id, Status.NO_CANDIDATE, "목표 보유 또는 예산/슬롯 조건을 만족하는 후보가 없습니다.");
            }
            var scored = new ArrayList<ScoredPurchase>();
            for (var candidate : candidates) {
                var features = ComponentCandidateFeatures.calculate(inventory, candidate, id, catalog, response, time, position, composition);
                double score = model.predict(features.features().stream().mapToDouble(Double::doubleValue).toArray());
                if (!Double.isFinite(score)) throw new IllegalStateException("모델 점수가 유한하지 않습니다.");
                scored.add(new ScoredPurchase(features, candidate.bundle().additionalCost(), score, 0, candidate.candidateSource(), candidate.preparationPatternCount(), candidate.preparationFutureRecipeLinkRate()));
            }
            scored.sort(Comparator.comparingDouble(ScoredPurchase::score).reversed());
            var ranked = new ArrayList<ScoredPurchase>();
            for (int i = 0; i < scored.size(); i++) {
                var candidate = scored.get(i);
                ranked.add(new ScoredPurchase(candidate.candidate(), candidate.additionalCost(), candidate.score(), i + 1, candidate.candidateSource(), candidate.preparationPatternCount(), candidate.preparationFutureRecipeLinkRate()));
            }
            return new TargetRecommendation(id, Status.OK, List.copyOf(ranked), null);
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
    public record ScoredPurchase(ComponentCandidateFeatures candidate, long additionalCost, double score, int rankWithinTarget,
            ComponentCandidateGenerator.Source candidateSource, Integer preparationPatternCount, Double preparationFutureRecipeLinkRate) {
        public ScoredPurchase(ComponentCandidateFeatures candidate, long additionalCost, double score, int rankWithinTarget) {
            this(candidate, additionalCost, score, rankWithinTarget, ComponentCandidateGenerator.Source.TARGET_PATH, null, null);
        }
    }
}
