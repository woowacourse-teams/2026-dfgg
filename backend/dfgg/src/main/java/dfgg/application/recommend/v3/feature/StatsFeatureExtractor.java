package dfgg.application.recommend.v3.feature;

import dfgg.application.recommend.v3.RecommendationQuery;
import dfgg.application.recommend.v3.generator.CounterLift;
import dfgg.application.recommend.v3.generator.CounterLiftCalculator;
import dfgg.application.recommend.v3.generator.PairScoreAggregate;
import dfgg.application.recommend.v3.generator.PairSynergyRetriever;
import dfgg.domain.itemstats.ChampionItemStats;
import dfgg.domain.itemstats.ChampionItemStatsRepository;
import dfgg.domain.itemstats.ChampionPairItemStats;
import dfgg.domain.itemstats.ChampionPairItemStatsRepository;
import dfgg.domain.itemstats.ItemMetaStats;
import dfgg.domain.itemstats.ItemMetaStatsRepository;
import dfgg.domain.itemstats.PairRelation;
import dfgg.domain.match.PatchVersion;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * 집계 통계에서 오는 feature를 채운다 — 챔피언 base rate, counter lift, 아군 관계 점수, 패치별 인기 변화.
 * <p>
 * generator가 낸 랭킹 점수와 달리, 여기서는 그 점수의 재료가 된 원값을 그대로 넘긴다.
 * 특히 counter는 lift·원 확률·base rate 셋을 각각 남긴다 — 이번 작업의 실패 유형
 * ("원래 안 사는 아이템이 counter 신호만으로 상승")은 셋을 구분해야 판별할 수 있다.
 */
@Component
public class StatsFeatureExtractor {

    private final ChampionItemStatsRepository championItemStatsRepository;
    private final ChampionPairItemStatsRepository pairRepository;
    private final ItemMetaStatsRepository itemMetaStatsRepository;
    private final PairSynergyRetriever pairSynergyRetriever;
    private final CounterLiftCalculator counterLiftCalculator;

    public StatsFeatureExtractor(
            ChampionItemStatsRepository championItemStatsRepository,
            ChampionPairItemStatsRepository pairRepository,
            ItemMetaStatsRepository itemMetaStatsRepository,
            PairSynergyRetriever pairSynergyRetriever,
            CounterLiftCalculator counterLiftCalculator
    ) {
        this.championItemStatsRepository = championItemStatsRepository;
        this.pairRepository = pairRepository;
        this.itemMetaStatsRepository = itemMetaStatsRepository;
        this.pairSynergyRetriever = pairSynergyRetriever;
        this.counterLiftCalculator = counterLiftCalculator;
    }

    /**
     * 질의 단위 조회를 한 번만 수행해 후보마다 재사용한다.
     *
     * <p>이걸 후보마다 다시 하면 같은 쿼리를 후보 수만큼 반복한다 — 챔피언 통계·아군 관계·적
     * 삼중항은 {@code itemId}와 무관하게 질의당 한 벌이다. 실측에서 후보 11개당 4배 낭비였고,
     * 학습 데이터 30만 query 규모에서는 시간 차이가 몇 시간 단위로 벌어진다.
     */
    public StatsContext prepare(RecommendationQuery query) {
        return new StatsContext(
                championGameCounts(query),
                purchaseCountsByItem(query),
                counterStatsByItem(query),
                pairSynergyRetriever.scoresByItem(
                        query.myChampionId(), query.allyChampionIds(), PairRelation.ALLY)
        );
    }

    public void extract(long itemId, RecommendationQuery query, FeatureVector vector) {
        extract(itemId, query, prepare(query), vector);
    }

    public void extract(long itemId, RecommendationQuery query, StatsContext context, FeatureVector vector) {
        ChampionBaseRate baseRate = context.baseRateOf(itemId);
        setBaseRate(baseRate, vector);
        setCounter(itemId, context, baseRate, vector);
        setAlly(itemId, context, vector);
        setMeta(itemId, query, vector);
    }

    // ── 챔피언 base rate ────────────────────────────────────────────────────

    /**
     * 관측된 챔피언이면 구매 0회도 {@code 0.0}으로 남긴다 — "0번 샀다"는 결측이 아니라
     * 강한 관측이다. 야스오가 9,343판 동안 존야를 한 번도 안 샀다는 사실이 바로 그 신호다.
     */
    private int[] championGameCounts(RecommendationQuery query) {
        List<ChampionItemStats> stats = championStats(query);
        return new int[]{
                stats.stream().mapToInt(ChampionItemStats::getChampionGameCountAll).max().orElse(0),
                stats.stream().mapToInt(ChampionItemStats::getChampionGameCountRecent).max().orElse(0)
        };
    }

    private Map<Long, int[]> purchaseCountsByItem(RecommendationQuery query) {
        Map<Long, int[]> byItem = new HashMap<>();
        for (ChampionItemStats stats : championStats(query)) {
            byItem.put(stats.getItemId(),
                    new int[]{stats.getPurchaseCountAll(), stats.getPurchaseCountRecent()});
        }
        return byItem;
    }

    private List<ChampionItemStats> championStats(RecommendationQuery query) {
        return championItemStatsRepository
                .findByChampionIdAndPosition(Math.toIntExact(query.myChampionId()), query.position());
    }

    private Map<Long, List<ChampionPairItemStats>> counterStatsByItem(RecommendationQuery query) {
        if (query.enemyChampionIds().isEmpty()) {
            return Map.of();
        }
        Map<Long, List<ChampionPairItemStats>> byItem = new HashMap<>();
        for (ChampionPairItemStats stats : pairRepository.findByMyChampionIdAndRelationAndOtherChampionIdIn(
                Math.toIntExact(query.myChampionId()), PairRelation.ENEMY,
                query.enemyChampionIds().stream().map(Math::toIntExact).toList())) {
            byItem.computeIfAbsent(stats.getItemId(), itemId -> new java.util.ArrayList<>()).add(stats);
        }
        return byItem;
    }

    private void setBaseRate(ChampionBaseRate baseRate, FeatureVector vector) {
        if (baseRate.gameCountAll() <= 0) {
            return;
        }
        double all = baseRate.rateAll();
        vector.set(FeatureName.CHAMPION_BASE_RATE_ALL, all);
        if (baseRate.gameCountRecent() > 0) {
            double recent = baseRate.rateRecent();
            vector.set(FeatureName.CHAMPION_BASE_RATE_RECENT, recent);
            // 0으로 나누지 않는다. 전체가 0인데 최근이 있으면 급등 신호이므로 크게 잡는다.
            vector.set(FeatureName.CHAMPION_BASE_RATE_RECENT_VS_ALL, all > 0 ? recent / all : (recent > 0 ? 2.0 : 1.0));
        }
    }

    // ── Counter ────────────────────────────────────────────────────────────

    private void setCounter(
            long itemId, StatsContext context, ChampionBaseRate baseRate, FeatureVector vector
    ) {
        if (baseRate.gameCountAll() <= 0) {
            return;
        }
        List<CounterLift> lifts = context.counterStatsByItem()
                .getOrDefault(itemId, List.of())
                .stream()
                .map(stats -> counterLiftCalculator.calculate(
                        stats.getCoCountAll(), stats.getPairGameCountAll(),
                        baseRate.purchaseCountAll(), baseRate.gameCountAll()))
                .toList();
        if (lifts.isEmpty()) {
            return;
        }

        List<Double> descending = lifts.stream()
                .map(CounterLift::lift)
                .sorted(Comparator.reverseOrder())
                .toList();
        vector.set(FeatureName.COUNTER_LIFT_MAX, descending.get(0));
        vector.set(FeatureName.COUNTER_LIFT_TOP1, descending.get(0));
        vector.set(FeatureName.COUNTER_LIFT_TOP2, descending.size() > 1 ? descending.get(1) : 0.0);
        vector.set(FeatureName.COUNTER_LIFT_MEAN,
                descending.stream().mapToDouble(Double::doubleValue).average().orElse(0.0));
        vector.set(FeatureName.COUNTER_PAIR_PROBABILITY_MAX,
                lifts.stream().mapToDouble(CounterLift::pairProbability).max().orElse(0.0));
    }

    // ── Ally ───────────────────────────────────────────────────────────────

    private void setAlly(long itemId, StatsContext context, FeatureVector vector) {
        PairScoreAggregate aggregate = context.allyScoresByItem().get(itemId);
        if (aggregate == null) {
            return;
        }
        vector.set(FeatureName.ALLY_SCORE_MAX, aggregate.max());
        vector.set(FeatureName.ALLY_SCORE_MEAN, aggregate.mean());
        vector.set(FeatureName.ALLY_SCORE_SUM, aggregate.sum());
        vector.set(FeatureName.ALLY_SCORE_TOP1, aggregate.top1());
        vector.set(FeatureName.ALLY_SCORE_TOP2, aggregate.top2());
    }

    // ── Meta (패치별 변화) ──────────────────────────────────────────────────

    /**
     * 패치 계열을 버전 순으로 정렬해 현재값과 직전·최근 3패치 평균 대비 변화를 낸다.
     * 문자열 정렬이면 16.10이 16.9보다 앞서므로 {@link PatchVersion}을 거친다.
     */
    private void setMeta(long itemId, RecommendationQuery query, FeatureVector vector) {
        List<ItemMetaStats> series = itemMetaStatsRepository
                .findByPositionAndItemId(query.position(), itemId);
        if (series.isEmpty()) {
            return;
        }
        Map<String, ItemMetaStats> byPatch = new HashMap<>();
        series.forEach(stats -> byPatch.put(stats.getPatch(), stats));

        List<ItemMetaStats> ordered = series.stream()
                .sorted(Comparator.comparing(stats -> PatchVersion.of(stats.getPatch())))
                .toList();

        ItemMetaStats current = byPatch.getOrDefault(query.patch(), ordered.get(ordered.size() - 1));
        double currentPickRate = pickRate(current);
        vector.set(FeatureName.ITEM_PICK_RATE_CURRENT_PATCH, currentPickRate);
        vector.set(FeatureName.ITEM_WIN_RATE_CURRENT_PATCH, winRate(current));

        int currentIndex = ordered.indexOf(current);
        if (currentIndex > 0) {
            ItemMetaStats previous = ordered.get(currentIndex - 1);
            vector.set(FeatureName.ITEM_PICK_RATE_DELTA_PREV_PATCH, currentPickRate - pickRate(previous));
            vector.set(FeatureName.ITEM_WIN_RATE_DELTA_PREV_PATCH, winRate(current) - winRate(previous));
        }

        List<ItemMetaStats> recentThree = ordered.subList(Math.max(0, currentIndex - 2), currentIndex + 1);
        double recentMean = recentThree.stream().mapToDouble(this::pickRate).average().orElse(currentPickRate);
        vector.set(FeatureName.ITEM_PICK_RATE_DELTA_3PATCH, currentPickRate - recentMean);
    }

    private double pickRate(ItemMetaStats stats) {
        return stats.getScopeGameCount() == 0 ? 0.0 : (double) stats.getPickCount() / stats.getScopeGameCount();
    }

    private double winRate(ItemMetaStats stats) {
        return stats.getPickCount() == 0 ? 0.0 : (double) stats.getWinCount() / stats.getPickCount();
    }

    /**
     * 질의 단위로 한 번만 읽는 통계 묶음. 후보마다 같은 조회를 반복하지 않기 위한 것이다.
     *
     * @param championGameCounts {@code [전체 판수, 최근 판수]}
     */
    public record StatsContext(
            int[] championGameCounts,
            Map<Long, int[]> purchaseCountsByItem,
            Map<Long, List<ChampionPairItemStats>> counterStatsByItem,
            Map<Long, PairScoreAggregate> allyScoresByItem
    ) {

        private ChampionBaseRate baseRateOf(long itemId) {
            if (championGameCounts[0] == 0) {
                return ChampionBaseRate.UNKNOWN;
            }
            int[] purchases = purchaseCountsByItem.getOrDefault(itemId, new int[]{0, 0});
            return new ChampionBaseRate(
                    purchases[0], championGameCounts[0], purchases[1], championGameCounts[1]);
        }
    }

    private record ChampionBaseRate(
            int purchaseCountAll, int gameCountAll, int purchaseCountRecent, int gameCountRecent
    ) {

        private static final ChampionBaseRate UNKNOWN = new ChampionBaseRate(0, 0, 0, 0);

        private double rateAll() {
            return (double) purchaseCountAll / gameCountAll;
        }

        private double rateRecent() {
            return (double) purchaseCountRecent / gameCountRecent;
        }
    }
}
