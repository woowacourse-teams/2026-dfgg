package dfgg.application.recommend.component;

import dfgg.application.recommend.context.CompositionFeatures;
import dfgg.application.recommend.v3.ranker.GradientBoostedTrees;
import dfgg.domain.match.ComponentCandidateGenerator;
import dfgg.domain.match.ItemInventory;
import dfgg.domain.match.PurchaseItemCatalog;
import dfgg.infrastructure.external.dto.ItemResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 목표 하나의 구매 후보를 기존 피처·모델로 평가하고 순위를 결정한다. */
public final class ComponentRanker {
    private final GradientBoostedTrees model;

    public ComponentRanker(GradientBoostedTrees model) {
        this.model = model;
    }

    public List<ScoredPurchase> rank(List<ComponentCandidateGenerator.Candidate> candidates,
            ItemInventory inventory, int targetItemId, PurchaseItemCatalog catalog, ItemResponse response,
            long gameTimeMs, String position, CompositionFeatures composition) {
        var scored = new ArrayList<ScoredPurchase>();
        for (var candidate : candidates) {
            var features = ComponentCandidateFeatures.calculate(inventory, candidate, targetItemId,
                    catalog, response, gameTimeMs, position, composition);
            scored.add(score(candidate, features));
        }
        // 안정 정렬로 동점 후보의 기존 생성 순서를 유지한다.
        scored.sort(Comparator.comparingDouble(ScoredPurchase::score).reversed());
        var ranked = new ArrayList<ScoredPurchase>();
        for (int i = 0; i < scored.size(); i++) {
            ranked.add(scored.get(i).rankedAt(i + 1));
        }
        return List.copyOf(ranked);
    }

    private ScoredPurchase score(ComponentCandidateGenerator.Candidate candidate, ComponentCandidateFeatures features) {
        double score = model.predict(features.features().stream().mapToDouble(Double::doubleValue).toArray());
        if (!Double.isFinite(score)) {
            throw new IllegalStateException("모델 점수가 유한하지 않습니다.");
        }
        return new ScoredPurchase(features, candidate.bundle().additionalCost(), score, 0,
                candidate.candidateSource(), candidate.policyItemId());
    }
}
