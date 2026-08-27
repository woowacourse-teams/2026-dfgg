package dfgg.application.recommend.fallback;

import dfgg.application.recommend.CandidateSimilarityScorer;
import dfgg.application.recommend.CandidateZoneMixer;
import dfgg.application.recommend.ExplorationZoneCandidateGenerator;
import dfgg.application.recommend.FinalScoreCalculator;
import dfgg.application.recommend.FinalScoreWeights;
import dfgg.application.recommend.ItemSimilarityScores;
import dfgg.application.recommend.MixedCandidates;
import dfgg.application.recommend.RankedItemCandidate;
import dfgg.application.recommend.SafeZoneCandidateGenerator;
import dfgg.infrastructure.config.RecommendationProperties;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 80% 안전 구역(PrefixSpan) + 20% 탐색 구역(카운터 maxSim) 후보를 합쳐
 * late-interaction 재정렬(finalScore)로 다음 아이템 후보를 랭킹하는 기본 경로.
 *
 * 합친 후보 수가 {@code prefixBackoffThreshold} 이하면 prefix를 한 칸씩 줄여(prefix
 * 백오프) 안전 구역 후보를 추가로 채운다 — 특정 챔피언 하나로 좁히면 깊은 prefix일수록
 * 정확히 일치하는 실측/마이닝 표본이 급격히 줄어(4~5코어 시점엔 매칭 0건도 흔함) 탐색
 * 구역만으로 응답이 빈약해지는 걸 막기 위함이다. 같은 아이템이 여러 깊이에서 나오면 더
 * 깊은(정밀한) prefix의 점수를 우선한다.
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
        List<RankedItemCandidate> explorationZoneRanked = explorationZoneCandidateGenerator
                .rankByMaxSimilarityToEnemies(
                        context.enemyChampionIds(), recommendationProperties.counterAlgorithmVersion(),
                        context.myChampionId(), context.position()
                );

        List<Long> prefix = context.purchasedItemIds();
        List<RankedItemCandidate> safeZoneRanked = safeZoneCandidateGenerator.rankNextItemCandidates(
                prefix, context.myChampionId(), context.position(),
                context.tier(), context.patch(), recommendationProperties.patternAlgorithmVersion(),
                recommendationProperties.anchoredPrefixLimit()
        );
        MixedCandidates mixed = candidateZoneMixer.mix(
                safeZoneRanked, explorationZoneRanked,
                recommendationProperties.totalCandidateCount(), recommendationProperties.safeZoneRatio()
        );
        Map<Long, Double> wilsonScoreByItemId = collectWilsonScores(mixed, context.purchasedItemIds());

        while (wilsonScoreByItemId.size() <= recommendationProperties.prefixBackoffThreshold() && !prefix.isEmpty()) {
            prefix = prefix.subList(0, prefix.size() - 1);
            List<RankedItemCandidate> backoffCandidates = safeZoneCandidateGenerator.rankNextItemCandidates(
                    prefix, context.myChampionId(), context.position(),
                    context.tier(), context.patch(), recommendationProperties.patternAlgorithmVersion(),
                    recommendationProperties.anchoredPrefixLimit()
            );
            safeZoneRanked = mergeKeepingFirstOccurrence(safeZoneRanked, backoffCandidates);
            mixed = candidateZoneMixer.mix(
                    safeZoneRanked, explorationZoneRanked,
                    recommendationProperties.totalCandidateCount(), recommendationProperties.safeZoneRatio()
            );
            wilsonScoreByItemId = collectWilsonScores(mixed, context.purchasedItemIds());
        }

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

    /**
     * 두 안전 구역 후보 목록을 합치되, 같은 아이템이 양쪽에 있으면 {@code preferred}(더
     * 깊은/정밀한 prefix에서 나온 쪽)의 점수를 유지한다. 합친 뒤 점수 내림차순으로 다시
     * 정렬한다 — {@link CandidateZoneMixer#mix}가 상위 N개만 자르므로, 정렬 없이 이어
     * 붙이면 뒤에 붙은 더 높은 점수 후보가 잘려나갈 수 있다.
     */
    private List<RankedItemCandidate> mergeKeepingFirstOccurrence(
            List<RankedItemCandidate> preferred, List<RankedItemCandidate> extra
    ) {
        Set<Long> seenItemIds = new LinkedHashSet<>();
        List<RankedItemCandidate> merged = new ArrayList<>(preferred.size() + extra.size());
        for (RankedItemCandidate candidate : preferred) {
            if (seenItemIds.add(candidate.itemId())) {
                merged.add(candidate);
            }
        }
        for (RankedItemCandidate candidate : extra) {
            if (seenItemIds.add(candidate.itemId())) {
                merged.add(candidate);
            }
        }
        return merged.stream()
                .sorted(Comparator.comparingDouble(RankedItemCandidate::score).reversed())
                .toList();
    }

    private Map<Long, Double> collectWilsonScores(MixedCandidates mixed, List<Long> purchasedItemIds) {
        Map<Long, Double> wilsonScoreByItemId = new LinkedHashMap<>();
        for (RankedItemCandidate candidate : mixed.safeZoneCandidates()) {
            if (!purchasedItemIds.contains(candidate.itemId())) {
                wilsonScoreByItemId.putIfAbsent(candidate.itemId(), candidate.score());
            }
        }
        for (RankedItemCandidate candidate : mixed.explorationZoneCandidates()) {
            if (!purchasedItemIds.contains(candidate.itemId())) {
                wilsonScoreByItemId.putIfAbsent(candidate.itemId(), 0.0);
            }
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
