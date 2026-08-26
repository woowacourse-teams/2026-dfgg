package dfgg.application.recommend.fallback;

import dfgg.application.recommend.CandidateSimilarityScorer;
import dfgg.application.recommend.CandidateZoneMixer;
import dfgg.application.recommend.ExplorationZoneCandidateGenerator;
import dfgg.application.recommend.FinalScoreCalculator;
import dfgg.application.recommend.FinalScoreWeights;
import dfgg.application.recommend.ItemSimilarityScores;
import dfgg.application.recommend.MixedCandidates;
import dfgg.application.recommend.RankedItemCandidate;
import dfgg.application.recommend.RankedSequentialPattern;
import dfgg.application.recommend.SafeZoneCandidateGenerator;
import dfgg.infrastructure.config.RecommendationProperties;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 80% 안전 구역(PrefixSpan) + 20% 탐색 구역(카운터 maxSim) 후보를 합쳐
 * late-interaction 재정렬(finalScore)로 다음 아이템 후보를 랭킹하는 기본 경로.
 */
@Component
public class PrimaryRecommendationStrategy implements RecommendationStrategy {

    private final SafeZoneCandidateGenerator safeZoneCandidateGenerator;
    private final ExplorationZoneCandidateGenerator explorationZoneCandidateGenerator;
    private final CandidateZoneMixer candidateZoneMixer;
    private final CandidateSimilarityScorer candidateSimilarityScorer;
    private final FinalScoreCalculator finalScoreCalculator;
    private final RecommendationProperties recommendationProperties;

    public PrimaryRecommendationStrategy(
            SafeZoneCandidateGenerator safeZoneCandidateGenerator,
            ExplorationZoneCandidateGenerator explorationZoneCandidateGenerator,
            CandidateZoneMixer candidateZoneMixer,
            CandidateSimilarityScorer candidateSimilarityScorer,
            FinalScoreCalculator finalScoreCalculator,
            RecommendationProperties recommendationProperties
    ) {
        this.safeZoneCandidateGenerator = safeZoneCandidateGenerator;
        this.explorationZoneCandidateGenerator = explorationZoneCandidateGenerator;
        this.candidateZoneMixer = candidateZoneMixer;
        this.candidateSimilarityScorer = candidateSimilarityScorer;
        this.finalScoreCalculator = finalScoreCalculator;
        this.recommendationProperties = recommendationProperties;
    }

    @Override
    public FallbackStage stage() {
        return FallbackStage.PRIMARY;
    }

    @Override
    public Optional<List<Long>> recommend(RecommendationContext context) {
        List<RankedSequentialPattern> safeZoneRanked = safeZoneCandidateGenerator.rankNextItemCandidates(
                context.purchasedItemIds(), context.myChampionId(), context.position(),
                context.tier(), context.patch(), recommendationProperties.patternAlgorithmVersion()
        );
        List<RankedItemCandidate> explorationZoneRanked = explorationZoneCandidateGenerator
                .rankByMaxSimilarityToEnemies(context.enemyChampionIds(), recommendationProperties.counterAlgorithmVersion());

        MixedCandidates mixed = candidateZoneMixer.mix(
                safeZoneRanked, explorationZoneRanked,
                recommendationProperties.totalCandidateCount(), recommendationProperties.safeZoneRatio()
        );

        Map<Long, Double> wilsonScoreByItemId = collectWilsonScores(mixed);
        if (wilsonScoreByItemId.isEmpty()) {
            return Optional.empty();
        }

        List<Long> itemIds = List.copyOf(wilsonScoreByItemId.keySet());
        List<ItemSimilarityScores> similarityScores = candidateSimilarityScorer.scoreItems(
                itemIds, context.myChampionId(), context.allyChampionIds(), context.enemyChampionIds(),
                recommendationProperties.identityAlgorithmVersion(), recommendationProperties.counterAlgorithmVersion()
        );

        Map<Long, Double> finalScoreByItemId = computeFinalScores(similarityScores, wilsonScoreByItemId);

        List<Long> ranked = itemIds.stream()
                .sorted(Comparator.comparingDouble(finalScoreByItemId::get).reversed())
                .toList();
        return Optional.of(ranked);
    }

    private Map<Long, Double> collectWilsonScores(MixedCandidates mixed) {
        Map<Long, Double> wilsonScoreByItemId = new LinkedHashMap<>();
        for (RankedSequentialPattern candidate : mixed.safeZoneCandidates()) {
            List<Long> items = candidate.pattern().getItems();
            Long nextItemId = items.get(items.size() - 1);
            wilsonScoreByItemId.putIfAbsent(nextItemId, candidate.wilsonLowerBound());
        }
        for (RankedItemCandidate candidate : mixed.explorationZoneCandidates()) {
            wilsonScoreByItemId.putIfAbsent(candidate.itemId(), 0.0);
        }
        return wilsonScoreByItemId;
    }

    private Map<Long, Double> computeFinalScores(
            List<ItemSimilarityScores> similarityScores, Map<Long, Double> wilsonScoreByItemId
    ) {
        FinalScoreWeights weights = new FinalScoreWeights(
                recommendationProperties.wilsonWeight(), recommendationProperties.myChampionWeight(),
                recommendationProperties.allyWeight(), recommendationProperties.enemyWeight()
        );

        Map<Long, Double> finalScoreByItemId = new LinkedHashMap<>();
        for (ItemSimilarityScores scores : similarityScores) {
            double finalScore = finalScoreCalculator.calculate(
                    wilsonScoreByItemId.get(scores.itemId()),
                    scores.cosineToMyChampion(),
                    scores.maxSimilarityToAllies(),
                    scores.maxSimilarityToEnemies(),
                    weights
            );
            finalScoreByItemId.put(scores.itemId(), finalScore);
        }
        return finalScoreByItemId;
    }
}
