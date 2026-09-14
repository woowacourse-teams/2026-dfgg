package dfgg.application.recommend.v3.generator;

import dfgg.application.recommend.v3.CandidateGenerator;
import dfgg.application.recommend.v3.CandidateSource;
import dfgg.application.recommend.v3.GeneratorResult;
import dfgg.application.recommend.v3.RecommendationQuery;
import dfgg.application.recommend.v3.ScoredItem;
import dfgg.application.utils.WilsonScoreCalculator;
import dfgg.domain.itemstats.ChampionItemRollup;
import dfgg.domain.itemstats.ChampionItemRollupRepository;
import dfgg.domain.itemstats.ChampionItemStats;
import dfgg.domain.itemstats.ChampionItemStatsRepository;
import dfgg.domain.itemstats.PairRelation;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * "우리 팀 조합 때문에 어떤 아이템이 좋은가"로 후보를 찾는다.
 * <p>
 * 아군 4명을 하나의 window로 묶지 않고 <b>각 아군과의 관계를 따로</b> 조회한 뒤 union한다.
 * 5명 전체를 한 덩어리로 보면 조합 수가 폭발해 표본이 사라지고, 무엇보다 "징크스 때문에 향로"와
 * "코그모 때문에 월석"을 구분할 수 없게 된다. 서포터처럼 아군에 따라 빌드가 갈리는 챔피언에서
 * 이 구분이 곧 이 generator의 값어치다.
 * <p>
 * 랭킹 점수로는 아군별 점수 중 최댓값을 쓴다 — 아군 하나와의 궁합이 결정적인 경우가 흔해서
 * 평균을 내면 그 신호가 묻힌다. 다만 개별 점수와 max/mean/sum/top1/top2를 모두 보존해
 * 어느 집계가 유효한지는 LTR이 고르게 한다.
 */
@Component
public class AllySynergyCandidateGenerator implements CandidateGenerator {

    private static final int MINIMUM_WIN_SAMPLES = 30;

    private final PairSynergyRetriever pairSynergyRetriever;
    private final ChampionItemStatsRepository championItemStatsRepository;
    private final ChampionItemRollupRepository championItemRollupRepository;
    private final WilsonScoreCalculator wilsonScoreCalculator;
    private final ChampionBaselineReader championBaselineReader;

    public AllySynergyCandidateGenerator(
            PairSynergyRetriever pairSynergyRetriever,
            ChampionItemStatsRepository championItemStatsRepository,
            ChampionItemRollupRepository championItemRollupRepository,
            WilsonScoreCalculator wilsonScoreCalculator,
            ChampionBaselineReader championBaselineReader
    ) {
        this.pairSynergyRetriever = pairSynergyRetriever;
        this.championItemStatsRepository = championItemStatsRepository;
        this.championItemRollupRepository = championItemRollupRepository;
        this.wilsonScoreCalculator = wilsonScoreCalculator;
        this.championBaselineReader = championBaselineReader;
    }

    @Override
    public CandidateSource source() {
        return CandidateSource.ALLY_SYNERGY;
    }

    @Override
    public GeneratorResult generate(RecommendationQuery query, int topK) {
        // lift 분모는 off-role이면 챔피언 전체로 물러선다. feature 추출기와 같은 곳에서 읽는다.
        ChampionBaseline baseline = championBaselineReader.read(query.myChampionId(), query.position());
        Map<Long, PairScoreAggregate> scoresByItem = pairSynergyRetriever.scoresByItem(
                query.myChampionId(), query.allyChampionIds(), PairRelation.ALLY,
                baseline.purchaseCountAllByItem(), baseline.gameCountAll());
        List<ChampionItemStats> positionStats =
                championItemStatsRepository.findByChampionIdAndPosition(
                        Math.toIntExact(query.myChampionId()), query.position());
        Map<Long, Map<Long, Double>> winLiftsByItem = pairSynergyRetriever.winLiftsByItem(
                query.myChampionId(), query.allyChampionIds(), PairRelation.ALLY,
                itemWinRates(positionStats), championWinRate(positionStats),
                MINIMUM_WIN_SAMPLES);

        if (!scoresByItem.isEmpty()) {
            List<ScoredItem> ranked = scoresByItem.entrySet().stream()
                    .filter(entry -> !query.purchasedItemIds().contains(entry.getKey()))
                    // 랭킹에는 최댓값 하나를 쓰지만 아군별 점수도 함께 남긴다. retriever가
                    // 이미 계산해 둔 값이라 여기서 버리면 나중에 다시 조회해야 한다.
                    .map(entry -> new ScoredItem(entry.getKey(), entry.getValue().max(),
                            entry.getValue().scoreByOtherChampionId(),
                            winLiftsByItem.getOrDefault(entry.getKey(), Map.of())))
                    .sorted(byScoreThenItemId())
                    .limit(topK)
                    .toList();
            return GeneratorResult.of(source(), ranked, PairBackoffLevel.TRIPLE.ordinal());
        }

        return GeneratorResult.of(source(), championBaseRate(query, topK), PairBackoffLevel.BASE_RATE.ordinal());
    }

    /**
     * 승률 lift의 분모 — 이 챔피언이 이 아이템을 샀을 때의 평소 승률.
     * 상대와 무관한 값이라 "이 아군과 함께일 때 특별히 잘 되는가"를 물을 수 있다.
     * 표본이 얇은 아이템은 빼둔다 — 분모가 튀면 lift도 튄다.
     */
    private Map<Long, Double> itemWinRates(List<ChampionItemStats> positionStats) {
        Map<Long, Double> winRateByItem = new java.util.HashMap<>();
        for (ChampionItemStats stats : positionStats) {
            if (stats.getPurchaseCountAll() >= MINIMUM_WIN_SAMPLES) {
                winRateByItem.put(stats.getItemId(),
                        (double) stats.getWinCountAll() / stats.getPurchaseCountAll());
            }
        }
        return winRateByItem;
    }

    /**
     * 이 챔피언·포지션의 아이템 무관 승률. 이중 차분의 마지막 항이다 —
     * 조합 승률을 이것으로 나눠야 "이 조합이 평소보다 잘 이기는 정도"가 나온다.
     */
    private double championWinRate(List<ChampionItemStats> positionStats) {
        return positionStats.stream()
                .filter(stats -> stats.getChampionGameCountAll() > 0)
                .mapToDouble(stats ->
                        (double) stats.getChampionWinCountAll() / stats.getChampionGameCountAll())
                .findFirst()
                .orElse(0.0);
    }

    /**
     * 어떤 아군과도 유의미한 표본이 없을 때의 마지막 수단. 조합 정보를 못 쓰니 이 챔피언이
     * 평소 사는 분포를 그대로 낸다 — Self-Synergy와 같은 신호가 되지만, backoff level이
     * "이건 조합 근거가 아니다"를 LTR에 알려주므로 이중으로 세지 않는다.
     */
    private List<ScoredItem> championBaseRate(RecommendationQuery query, int topK) {
        int championId = Math.toIntExact(query.myChampionId());
        List<ChampionItemStats> positionStats =
                championItemStatsRepository.findByChampionIdAndPosition(championId, query.position());
        if (!positionStats.isEmpty()) {
            return positionStats.stream()
                    .filter(stat -> !query.purchasedItemIds().contains(stat.getItemId()))
                    .map(stat -> new ScoredItem(stat.getItemId(), wilsonScoreCalculator.lowerBound(
                            stat.getPurchaseCountAll(), stat.getChampionGameCountAll())))
                    .sorted(byScoreThenItemId())
                    .limit(topK)
                    .toList();
        }

        List<ChampionItemRollup> rollup = championItemRollupRepository.findByChampionId(championId);
        return rollup.stream()
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
