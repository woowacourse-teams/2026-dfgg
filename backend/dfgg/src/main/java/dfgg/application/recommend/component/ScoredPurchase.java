package dfgg.application.recommend.component;

import dfgg.domain.match.ComponentCandidateGenerator;

/** 모델이 평가한 구매 묶음과 평가에 사용한 피처·후보 출처를 함께 보존한다. */
public record ScoredPurchase(
        ComponentCandidateFeatures candidate,
        long additionalCost,
        double score,
        int rankWithinTarget,
        ComponentCandidateGenerator.Source candidateSource,
        Integer preparationPatternCount,
        Double preparationFutureRecipeLinkRate
) {
    public ScoredPurchase(ComponentCandidateFeatures candidate, long additionalCost, double score, int rankWithinTarget) {
        this(candidate, additionalCost, score, rankWithinTarget, ComponentCandidateGenerator.Source.TARGET_PATH, null, null);
    }

    public ScoredPurchase rankedAt(int rank) {
        return new ScoredPurchase(candidate, additionalCost, score, rank, candidateSource,
                preparationPatternCount, preparationFutureRecipeLinkRate);
    }
}
