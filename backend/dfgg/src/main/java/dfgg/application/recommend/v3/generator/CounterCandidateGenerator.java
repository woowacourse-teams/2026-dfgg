package dfgg.application.recommend.v3.generator;

import dfgg.application.recommend.v3.CandidateGenerator;
import dfgg.application.recommend.v3.CandidateSource;
import dfgg.application.recommend.v3.GeneratorResult;
import dfgg.application.recommend.v3.RecommendationQuery;
import dfgg.application.recommend.v3.ScoredItem;
import dfgg.application.utils.WilsonScoreCalculator;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.itemstats.ChampionItemRollup;
import dfgg.domain.itemstats.ChampionItemRollupRepository;
import dfgg.domain.itemstats.ChampionItemStats;
import dfgg.domain.itemstats.ChampionItemStatsRepository;
import dfgg.domain.itemstats.ChampionPairItemStats;
import dfgg.domain.itemstats.ChampionPairItemStatsRepository;
import dfgg.domain.itemstats.PairRelation;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * "현재 적 조합 때문에 어떤 아이템의 가치가 오르는가"로 후보를 찾는다.
 * <p>
 * 여기서는 {@code [내 챔피언 + 적 챔피언 + 아이템]} 삼중항을 쓴다.
 * 구매자가 키에 들어 있으므로 남의 아이템이 내 근거가 될 수 없다.
 * 점수는 raw 확률이 아니라 {@code P(item|나,적) / P(item|나)} lift이며,
 * 분모가 내 챔피언 자신의 구매율이라 "내가 원래 안 사는 아이템"이라는 사실이 계산에 직접 들어간다.
 * <p>
 * 그렇다고 AD/AP를 막지는 않는다. lift·원 확률·base rate 셋을 따로 LTR에 넘겨,
 * "base rate가 바닥인데 lift만 높은 후보"를 모델이 학습으로 눌러주게 한다.
 * 규칙으로 막으면 AD 르블랑 같은 비정형 빌드의 정답까지 지워진다.
 */
@Component
public class CounterCandidateGenerator implements CandidateGenerator {

    private final ChampionPairItemStatsRepository pairRepository;
    private final ChampionItemStatsRepository championItemStatsRepository;
    private final ChampionItemRollupRepository championItemRollupRepository;
    private final CounterLiftCalculator counterLiftCalculator;
    private final WilsonScoreCalculator wilsonScoreCalculator;
    private final int minimumPairGames;

    public CounterCandidateGenerator(
            ChampionPairItemStatsRepository pairRepository,
            ChampionItemStatsRepository championItemStatsRepository,
            ChampionItemRollupRepository championItemRollupRepository,
            CounterLiftCalculator counterLiftCalculator,
            WilsonScoreCalculator wilsonScoreCalculator,
            @Value("${recommendation.pair-synergy.minimum-pair-games}") int minimumPairGames
    ) {
        this.pairRepository = pairRepository;
        this.championItemStatsRepository = championItemStatsRepository;
        this.championItemRollupRepository = championItemRollupRepository;
        this.counterLiftCalculator = counterLiftCalculator;
        this.wilsonScoreCalculator = wilsonScoreCalculator;
        this.minimumPairGames = minimumPairGames;
    }

    @Override
    public CandidateSource source() {
        return CandidateSource.COUNTER;
    }

    @Override
    public GeneratorResult generate(RecommendationQuery query, int topK) {
        Map<Long, Integer> baseCountByItem = baseCounts(query);
        int baseGameCount = baseGameCount(query);

        // 아이템 → (적 챔피언 → lift). 적별 lift를 개별 보존한 뒤 집계한다.
        Map<Long, Map<Long, Double>> liftByItemAndEnemy = new HashMap<>();
        for (ChampionPairItemStats stats : enemyStats(query)) {
            if (stats.getPairGameCountAll() < minimumPairGames
                    || query.purchasedItemIds().contains(stats.getItemId())) {
                continue;
            }
            CounterLift lift = counterLiftCalculator.calculate(
                    stats.getCoCountAll(), stats.getPairGameCountAll(),
                    baseCountByItem.getOrDefault(stats.getItemId(), 0), baseGameCount
            );
            liftByItemAndEnemy
                    .computeIfAbsent(stats.getItemId(), itemId -> new HashMap<>())
                    .put(Long.valueOf(stats.getOtherChampionId()), lift.lift());
        }

        if (liftByItemAndEnemy.isEmpty()) {
            return GeneratorResult.of(source(), championBaseRate(query, topK),
                    PairBackoffLevel.BASE_RATE.ordinal());
        }

        List<ScoredItem> ranked = liftByItemAndEnemy.entrySet().stream()
                .map(entry -> new ScoredItem(
                        entry.getKey(), PairScoreAggregate.of(entry.getValue()).max()))
                .sorted(byScoreThenItemId())
                .limit(topK)
                .toList();
        return GeneratorResult.of(source(), ranked, PairBackoffLevel.TRIPLE.ordinal());
    }

    /**
     * 적 하나에 대한 아이템별 counter 근거. lift·원 확률·base rate를 모두 담아 돌려주므로
     * feature extraction이 같은 계산을 되풀이하지 않고 그대로 쓸 수 있다.
     */
    public Map<Long, CounterLift> liftsByItem(long myChampionId, ChampionPosition position, long enemyChampionId) {
        Map<Long, Integer> baseCountByItem = baseCounts(myChampionId, position);
        int baseGameCount = baseGameCount(myChampionId, position);

        Map<Long, CounterLift> liftByItem = new HashMap<>();
        for (ChampionPairItemStats stats : pairRepository.findByMyChampionIdAndRelationAndOtherChampionIdIn(
                Math.toIntExact(myChampionId), PairRelation.ENEMY, List.of(Math.toIntExact(enemyChampionId)))) {
            liftByItem.put(stats.getItemId(), counterLiftCalculator.calculate(
                    stats.getCoCountAll(), stats.getPairGameCountAll(),
                    baseCountByItem.getOrDefault(stats.getItemId(), 0), baseGameCount
            ));
        }
        return liftByItem;
    }

    private List<ChampionPairItemStats> enemyStats(RecommendationQuery query) {
        if (query.enemyChampionIds().isEmpty()) {
            return List.of();
        }
        return pairRepository.findByMyChampionIdAndRelationAndOtherChampionIdIn(
                Math.toIntExact(query.myChampionId()), PairRelation.ENEMY,
                query.enemyChampionIds().stream().map(Math::toIntExact).toList()
        );
    }

    private Map<Long, Integer> baseCounts(RecommendationQuery query) {
        return baseCounts(query.myChampionId(), query.position());
    }

    private Map<Long, Integer> baseCounts(long myChampionId, ChampionPosition position) {
        Map<Long, Integer> countByItem = new HashMap<>();
        for (ChampionItemStats stats : positionStats(myChampionId, position)) {
            countByItem.put(stats.getItemId(), stats.getPurchaseCountAll());
        }
        if (!countByItem.isEmpty()) {
            return countByItem;
        }
        for (ChampionItemRollup stats : championItemRollupRepository.findByChampionId(Math.toIntExact(myChampionId))) {
            countByItem.put(stats.getItemId(), stats.getPurchaseCountAll());
        }
        return countByItem;
    }

    private int baseGameCount(RecommendationQuery query) {
        return baseGameCount(query.myChampionId(), query.position());
    }

    private int baseGameCount(long myChampionId, ChampionPosition position) {
        int fromPosition = positionStats(myChampionId, position).stream()
                .mapToInt(ChampionItemStats::getChampionGameCountAll)
                .max()
                .orElse(0);
        if (fromPosition > 0) {
            return fromPosition;
        }
        return championItemRollupRepository.findByChampionId(Math.toIntExact(myChampionId)).stream()
                .mapToInt(ChampionItemRollup::getChampionGameCountAll)
                .max()
                .orElse(0);
    }

    private List<ChampionItemStats> positionStats(long myChampionId, ChampionPosition position) {
        return championItemStatsRepository.findByChampionIdAndPosition(Math.toIntExact(myChampionId), position);
    }

    /** 만난 적 있는 적이 하나도 없을 때의 마지막 수단. backoff level이 "조합 근거가 아니다"를 알린다. */
    private List<ScoredItem> championBaseRate(RecommendationQuery query, int topK) {
        List<ChampionItemStats> positionStats = positionStats(query.myChampionId(), query.position());
        if (!positionStats.isEmpty()) {
            return positionStats.stream()
                    .filter(stat -> !query.purchasedItemIds().contains(stat.getItemId()))
                    .map(stat -> new ScoredItem(stat.getItemId(), wilsonScoreCalculator.lowerBound(
                            stat.getPurchaseCountAll(), stat.getChampionGameCountAll())))
                    .sorted(byScoreThenItemId())
                    .limit(topK)
                    .toList();
        }
        return championItemRollupRepository.findByChampionId(Math.toIntExact(query.myChampionId())).stream()
                .filter(stat -> !query.purchasedItemIds().contains(stat.getItemId()))
                .map(stat -> new ScoredItem(stat.getItemId(), wilsonScoreCalculator.lowerBound(
                        stat.getPurchaseCountAll(), stat.getChampionGameCountAll())))
                .sorted(byScoreThenItemId())
                .limit(topK)
                .toList();
    }

    private Comparator<ScoredItem> byScoreThenItemId() {
        return Comparator.comparingDouble(ScoredItem::score).reversed().thenComparing(ScoredItem::itemId);
    }
}
