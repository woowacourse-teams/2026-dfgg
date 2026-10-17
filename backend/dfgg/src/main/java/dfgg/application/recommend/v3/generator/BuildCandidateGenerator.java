package dfgg.application.recommend.v3.generator;

import dfgg.application.ChampionPositionNormalizer;
import dfgg.application.recommend.v3.CandidateGenerator;
import dfgg.application.recommend.v3.CandidateSource;
import dfgg.application.recommend.v3.GeneratorResult;
import dfgg.application.recommend.v3.RecommendationQuery;
import dfgg.application.recommend.v3.ScoredItem;
import dfgg.application.utils.WilsonScoreCalculator;
import dfgg.domain.itemstats.ChampionItemStats;
import dfgg.domain.itemstats.ChampionItemStatsRepository;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import dfgg.domain.match.RecentPatchWindow;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * "현재 build와 구매 순서를 볼 때 다음에 무엇을 사는가"로 후보를 찾는다.
 *
 * <p>표본이 없으면 단계적으로 물러선다 — 정확 prefix → 마지막 아이템 전개 → 챔피언 전반.
 * 깊은 prefix일수록 정확히 일치하는 표본이 급격히 줄어(4~5코어에선 0건도 흔하다) 물러설
 * 길이 없으면 후보가 비어버린다. 어디까지 물러섰는지는 결과에 남겨 LTR이 신뢰도를 학습한다.
 *
 * <p>각 단계에서 후보를 <b>전체 집계 상위 K와 최근 윈도 상위 K의 union</b>으로 뽑는다.
 * 갓 버프된 아이템은 전체 표본에 묻혀 상위에 못 드는데, 후보로조차 올라오지 않으면 랭커가
 * 아무리 좋아도 복구할 수 없다. 반대로 너프된 아이템이 후보에 남는 건 랭커가 눌러서 해결된다.
 */
@Component
public class BuildCandidateGenerator implements CandidateGenerator {

    private final NormalizedMatchParticipantRepository participantRepository;
    private final ChampionItemStatsRepository championItemStatsRepository;
    private final ChampionPositionNormalizer positionNormalizer;
    private final WilsonScoreCalculator wilsonScoreCalculator;

    /**
     * 집계({@code ItemStatsAggregationService})가 쓰는 값과 반드시 같아야 한다. 서로 다르면
     * {@code champion_item_stats._recent}(집계 시점 윈도)와 전개 통계(서빙 시점 윈도)가
     * 다른 "최근"을 뜻하게 되고, 그 어긋남은 추천 결과만 봐서는 드러나지 않는다.
     */
    private final int recentPatchWindowSize;

    public BuildCandidateGenerator(
            NormalizedMatchParticipantRepository participantRepository,
            ChampionItemStatsRepository championItemStatsRepository,
            ChampionPositionNormalizer positionNormalizer,
            WilsonScoreCalculator wilsonScoreCalculator,
            @Value("${recommendation.recent-patch-window-size}") int recentPatchWindowSize
    ) {
        this.participantRepository = participantRepository;
        this.championItemStatsRepository = championItemStatsRepository;
        this.positionNormalizer = positionNormalizer;
        this.wilsonScoreCalculator = wilsonScoreCalculator;
        this.recentPatchWindowSize = recentPatchWindowSize;
    }

    @Override
    public CandidateSource source() {
        return CandidateSource.BUILD;
    }

    @Override
    public GeneratorResult generate(RecommendationQuery query, int topK) {
        Collection<String> recentPatches = recentPatches();

        List<ScoredItem> exactPrefix = rankTransitions(exactPrefixRows(query, recentPatches), topK);
        if (!exactPrefix.isEmpty()) {
            return GeneratorResult.of(source(), exactPrefix, BuildBackoffLevel.EXACT_PREFIX.ordinal());
        }

        List<ScoredItem> lastItem = rankTransitions(lastItemRows(query, recentPatches), topK);
        if (!lastItem.isEmpty()) {
            return GeneratorResult.of(source(), lastItem, BuildBackoffLevel.LAST_ITEM.ordinal());
        }

        return GeneratorResult.of(source(), championLevel(query, topK), BuildBackoffLevel.CHAMPION.ordinal());
    }

    private List<Object[]> exactPrefixRows(RecommendationQuery query, Collection<String> recentPatches) {
        String prefix = query.purchasedItemIds().stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        return participantRepository.findNextItemAfterExactPrefix(
                query.myChampionId(), riotPositions(query), prefix,
                query.purchasedItemCount() + 1, recentPatches
        );
    }

    private List<Object[]> lastItemRows(RecommendationQuery query, Collection<String> recentPatches) {
        if (query.purchasedItemIds().isEmpty()) {
            return List.of();
        }
        Long lastItemId = query.purchasedItemIds().get(query.purchasedItemCount() - 1);
        return participantRepository.findNextItemAfterLastItem(
                query.myChampionId(), riotPositions(query), lastItemId, recentPatches
        );
    }

    /**
     * 전개 통계 행을 후보로 바꾼다. 점수는 "이 전개 중 이 아이템이 차지하는 비율"의 Wilson 하한이라
     * 표본이 얇을수록 보수적으로 깎인다. 승률이 아니라 전개 비율을 재는 이유는 Build의 질문이
     * "무엇을 사는가"이지 "무엇이 이기는가"가 아니기 때문이다 — 승률은 LTR이 별도 feature로 본다.
     */
    private List<ScoredItem> rankTransitions(List<Object[]> rows, int topK) {
        if (rows.isEmpty()) {
            return List.of();
        }
        int totalAll = rows.stream().mapToInt(row -> intAt(row, 1)).sum();
        int totalRecent = rows.stream().mapToInt(row -> intAt(row, 3)).sum();

        List<TransitionScore> scores = rows.stream()
                .map(row -> new TransitionScore(
                        Long.valueOf((String) row[0]),
                        wilsonScoreCalculator.lowerBound(intAt(row, 1), totalAll),
                        wilsonScoreCalculator.lowerBound(intAt(row, 3), totalRecent)
                ))
                .toList();
        return unionOfTopK(scores, topK);
    }

    private List<ScoredItem> championLevel(RecommendationQuery query, int topK) {
        List<ChampionItemStats> stats = championItemStatsRepository
                .findByChampionIdAndPosition(Math.toIntExact(query.myChampionId()), query.position());

        List<TransitionScore> scores = stats.stream()
                .filter(stat -> !query.purchasedItemIds().contains(stat.getItemId()))
                .map(stat -> new TransitionScore(
                        stat.getItemId(),
                        wilsonScoreCalculator.lowerBound(
                                stat.getPurchaseCountAll(), stat.getChampionGameCountAll()),
                        wilsonScoreCalculator.lowerBound(
                                stat.getPurchaseCountRecent(), stat.getChampionGameCountRecent())
                ))
                .toList();
        return unionOfTopK(scores, topK);
    }

    /**
     * 전체 기준 상위 K와 최근 기준 상위 K를 합친 뒤, 둘 중 높은 점수로 다시 정렬해 K개로 자른다.
     * union이 recall을 지키고, {@code max}가 "역사적으로 강하거나 지금 강하거나"를 한 값으로 만든다.
     * 두 원점수는 이후 feature extraction에서 다시 조회해 LTR에 각각 넘긴다.
     */
    private List<ScoredItem> unionOfTopK(List<TransitionScore> scores, int topK) {
        Set<Long> retrieved = new LinkedHashSet<>();
        retrieved.addAll(topKBy(scores, TransitionScore::scoreAll, topK));
        retrieved.addAll(topKBy(scores, TransitionScore::scoreRecent, topK));

        List<ScoredItem> ranked = new ArrayList<>();
        for (TransitionScore score : scores) {
            if (retrieved.contains(score.itemId())) {
                ranked.add(new ScoredItem(score.itemId(), score.bestScore()));
            }
        }
        return ranked.stream()
                .sorted(Comparator.comparingDouble(ScoredItem::score).reversed()
                        .thenComparing(ScoredItem::itemId))
                .limit(topK)
                .toList();
    }

    private List<Long> topKBy(
            List<TransitionScore> scores,
            java.util.function.ToDoubleFunction<TransitionScore> scoreOf,
            int topK
    ) {
        return scores.stream()
                .filter(score -> scoreOf.applyAsDouble(score) > 0.0)
                .sorted(Comparator.comparingDouble(scoreOf).reversed()
                        .thenComparing(TransitionScore::itemId))
                .limit(topK)
                .map(TransitionScore::itemId)
                .toList();
    }

    private Collection<String> recentPatches() {
        RecentPatchWindow window = RecentPatchWindow.of(participantRepository.findDistinctPatches(), recentPatchWindowSize);
        if (window.isEmpty()) {
            return List.of("");
        }
        return window.patches();
    }

    private List<String> riotPositions(RecommendationQuery query) {
        return positionNormalizer.riotValuesOf(query.position());
    }

    private int intAt(Object[] row, int index) {
        return ((Number) row[index]).intValue();
    }

    private record TransitionScore(Long itemId, double scoreAll, double scoreRecent) {

        private double bestScore() {
            return Math.max(scoreAll, scoreRecent);
        }
    }
}
