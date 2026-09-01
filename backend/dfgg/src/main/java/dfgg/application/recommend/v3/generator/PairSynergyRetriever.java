package dfgg.application.recommend.v3.generator;

import dfgg.application.utils.WilsonScoreCalculator;
import dfgg.domain.itemstats.ChampionPairItemStats;
import dfgg.domain.itemstats.ChampionPairItemStatsRepository;
import dfgg.domain.itemstats.PairRelation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * {@code [내 챔피언 + 상대 챔피언 + 아이템]} 삼중항 점수를 상대별로 따로 읽어온다.
 * Ally-Synergy(아군)와 Counter(적)가 같은 구조를 쓰므로 relation만 갈아끼워 공유한다.
 * <p>
 * 상대별 점수를 합쳐서 돌려주지 않는 것이 핵심이다. 5명을 하나의 window로 뭉치면
 * "누구 때문에 이 아이템이 좋은가"가 사라진다.
 * 집계는 {@link AllyScoreAggregate}가 하되 개별 점수를 함께 들고 있는다.
 * <p>
 * 함께한 판이 {@code minimumPairGames} 미만인 상대는 아예 제외한다. 한두 판 같이 한 조합에서
 * 나온 100% 구매율은 궁합이 아니라 우연이고, 그걸 점수로 올리면 표본이 얇을수록 강한 신호가 된다.
 */
@Component
public class PairSynergyRetriever {

    private final ChampionPairItemStatsRepository pairRepository;
    private final WilsonScoreCalculator wilsonScoreCalculator;
    private final int minimumPairGames;

    public PairSynergyRetriever(
            ChampionPairItemStatsRepository pairRepository,
            WilsonScoreCalculator wilsonScoreCalculator,
            @Value("${recommendation.pair-synergy.minimum-pair-games}") int minimumPairGames
    ) {
        this.pairRepository = pairRepository;
        this.wilsonScoreCalculator = wilsonScoreCalculator;
        this.minimumPairGames = minimumPairGames;
    }

    /**
     * 아이템별로 "상대 챔피언 → 점수" 묶음을 만든다.
     * 반환된 map에 없는 아이템은 어떤 상대와도 유의미하게 관측되지 않았다는 뜻이다.
     */
    public Map<Long, AllyScoreAggregate> scoresByItem(
            long myChampionId, List<Long> otherChampionIds, PairRelation relation
    ) {
        Map<Long, Map<Long, Double>> scoreByItemAndOther = new HashMap<>();
        for (ChampionPairItemStats stats : findStats(myChampionId, otherChampionIds, relation)) {
            if (stats.getPairGameCountAll() < minimumPairGames) {
                continue;
            }
            scoreByItemAndOther
                    .computeIfAbsent(stats.getItemId(), itemId -> new HashMap<>())
                    .put(Long.valueOf(stats.getOtherChampionId()), score(stats));
        }

        Map<Long, AllyScoreAggregate> aggregateByItem = new HashMap<>();
        scoreByItemAndOther.forEach((itemId, scoreByOther) ->
                aggregateByItem.put(itemId, AllyScoreAggregate.of(scoreByOther)));
        return aggregateByItem;
    }

    private List<ChampionPairItemStats> findStats(
            long myChampionId, List<Long> otherChampionIds, PairRelation relation
    ) {
        if (otherChampionIds.isEmpty()) {
            return List.of();
        }
        return pairRepository.findByMyChampionIdAndRelationAndOtherChampionIdIn(
                Math.toIntExact(myChampionId), relation,
                otherChampionIds.stream().map(Math::toIntExact).toList()
        );
    }

    /**
     * {@code P(item | 내 챔피언, 상대 챔피언)}의 Wilson 하한. 전체 집계와 최근 윈도 중 높은 쪽을 쓴다 —
     * 갓 버프된 아이템이 전체 표본에 묻혀 후보에서 빠지는 걸 막기 위함이다.
     */
    private double score(ChampionPairItemStats stats) {
        double all = wilsonScoreCalculator.lowerBound(stats.getCoCountAll(), stats.getPairGameCountAll());
        double recent = wilsonScoreCalculator.lowerBound(
                stats.getCoCountRecent(), stats.getPairGameCountRecent());
        return Math.max(all, recent);
    }
}
