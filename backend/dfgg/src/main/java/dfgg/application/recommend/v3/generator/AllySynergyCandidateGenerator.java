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
 *
 * <p>아군 4명을 하나의 window로 묶지 않고 <b>각 아군과의 관계를 따로</b> 조회한 뒤 union한다.
 * 5명 전체를 한 덩어리로 보면 조합 수가 폭발해 표본이 사라지고, 무엇보다 "징크스 때문에 향로"와
 * "코그모 때문에 월석"을 구분할 수 없게 된다. 서포터처럼 아군에 따라 빌드가 갈리는 챔피언에서
 * 이 구분이 곧 이 generator의 값어치다.
 *
 * <p>랭킹 점수로는 아군별 점수 중 최댓값을 쓴다 — 아군 하나와의 궁합이 결정적인 경우가 흔해서
 * 평균을 내면 그 신호가 묻힌다. 다만 개별 점수와 max/mean/sum/top1/top2를 모두 보존해
 * 어느 집계가 유효한지는 LTR이 고르게 한다.
 */
@Component
public class AllySynergyCandidateGenerator implements CandidateGenerator {

    private final PairSynergyRetriever pairSynergyRetriever;
    private final ChampionItemStatsRepository championItemStatsRepository;
    private final ChampionItemRollupRepository championItemRollupRepository;
    private final WilsonScoreCalculator wilsonScoreCalculator;

    public AllySynergyCandidateGenerator(
            PairSynergyRetriever pairSynergyRetriever,
            ChampionItemStatsRepository championItemStatsRepository,
            ChampionItemRollupRepository championItemRollupRepository,
            WilsonScoreCalculator wilsonScoreCalculator
    ) {
        this.pairSynergyRetriever = pairSynergyRetriever;
        this.championItemStatsRepository = championItemStatsRepository;
        this.championItemRollupRepository = championItemRollupRepository;
        this.wilsonScoreCalculator = wilsonScoreCalculator;
    }

    @Override
    public CandidateSource source() {
        return CandidateSource.ALLY_SYNERGY;
    }

    @Override
    public GeneratorResult generate(RecommendationQuery query, int topK) {
        Map<Long, AllyScoreAggregate> scoresByItem = pairSynergyRetriever.scoresByItem(
                query.myChampionId(), query.allyChampionIds(), PairRelation.ALLY);

        if (!scoresByItem.isEmpty()) {
            List<ScoredItem> ranked = scoresByItem.entrySet().stream()
                    .map(entry -> new ScoredItem(entry.getKey(), entry.getValue().max()))
                    .sorted(byScoreThenItemId())
                    .limit(topK)
                    .toList();
            return GeneratorResult.of(source(), ranked, PairBackoffLevel.TRIPLE.ordinal());
        }

        return GeneratorResult.of(source(), championBaseRate(query, topK), PairBackoffLevel.BASE_RATE.ordinal());
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
                    .map(stat -> new ScoredItem(stat.getItemId(), wilsonScoreCalculator.lowerBound(
                            stat.getPurchaseCountAll(), stat.getChampionGameCountAll())))
                    .sorted(byScoreThenItemId())
                    .limit(topK)
                    .toList();
        }

        List<ChampionItemRollup> rollup = championItemRollupRepository.findByChampionId(championId);
        return rollup.stream()
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
