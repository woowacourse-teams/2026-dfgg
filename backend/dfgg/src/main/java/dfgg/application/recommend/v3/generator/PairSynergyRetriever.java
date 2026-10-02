package dfgg.application.recommend.v3.generator;

import dfgg.domain.itemstats.ChampionPairItemStats;
import dfgg.domain.itemstats.ChampionPairItemStatsRepository;
import dfgg.domain.itemstats.PairRelation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * {@code [내 챔피언 + 상대 챔피언 + 아이템]} 삼중항 lift를 상대별로 따로 읽어온다.
 * <p>
 * 점수는 {@code P(item | 나, 상대) / P(item | 나)}다. 한때 Wilson 하한 확률을 썼는데,
 * 그러면 "원딜이면 다 사는 아이템"이 모든 아군에 대해 높은 점수를 받아 근거로 둔갑했다.
 * lift는 <b>평소 대비</b>를 보므로 그런 아이템이 1 근처로 수축한다.
 * Ally-Synergy(아군)와 Counter(적)가 같은 구조를 쓰므로 relation만 갈아끼워 공유한다.
 * <p>
 * 상대별 점수를 합쳐서 돌려주지 않는 것이 핵심이다. 5명을 하나의 window로 뭉치면
 * "누구 때문에 이 아이템이 좋은가"가 사라진다.
 * 집계는 {@link PairScoreAggregate}가 하되 개별 점수를 함께 들고 있는다.
 * <p>
 * 함께한 판이 {@code minimumPairGames} 미만인 상대는 아예 제외한다. 한두 판 같이 한 조합에서
 * 나온 100% 구매율은 궁합이 아니라 우연이고, 그걸 점수로 올리면 표본이 얇을수록 강한 신호가 된다.
 */
@Component
public class PairSynergyRetriever {

    private final ChampionPairItemStatsRepository pairRepository;
    private final PairLiftCalculator pairLiftCalculator;
    private final int minimumPairGames;
    private final double minimumBaseRate;

    public PairSynergyRetriever(
            ChampionPairItemStatsRepository pairRepository,
            PairLiftCalculator pairLiftCalculator,
            @Value("${recommendation.pair-synergy.minimum-pair-games}") int minimumPairGames,
            @Value("${recommendation.ally-synergy.minimum-base-rate}") double minimumBaseRate
    ) {
        this.pairRepository = pairRepository;
        this.pairLiftCalculator = pairLiftCalculator;
        this.minimumPairGames = minimumPairGames;
        this.minimumBaseRate = minimumBaseRate;
    }

    /**
     * 아이템별로 "상대 챔피언 → 점수" 묶음을 만든다.
     * 반환된 map에 없는 아이템은 어떤 상대와도 유의미하게 관측되지 않았다는 뜻이다.
     *
     * @param baseCountByItem 내 챔피언이 각 아이템을 산 판 수(상대 무관) — lift의 분모
     * @param baseGameCount   내 챔피언이 치른 판 수
     */
    public Map<Long, PairScoreAggregate> scoresByItem(
            long myChampionId, List<Long> otherChampionIds, PairRelation relation,
            Map<Long, Integer> baseCountByItem, int baseGameCount
    ) {
        Map<Long, Map<Long, Double>> scoreByItemAndOther = new HashMap<>();
        for (ChampionPairItemStats stats : findStats(myChampionId, otherChampionIds, relation)) {
            if (stats.getPairGameCountAll() < minimumPairGames
                    || belowBaseRateFloor(stats.getItemId(), baseCountByItem, baseGameCount)) {
                continue;
            }
            scoreByItemAndOther
                    .computeIfAbsent(stats.getItemId(), itemId -> new HashMap<>())
                    .put(Long.valueOf(stats.getOtherChampionId()),
                            lift(stats, baseCountByItem, baseGameCount));
        }

        Map<Long, PairScoreAggregate> aggregateByItem = new HashMap<>();
        scoreByItemAndOther.forEach((itemId, scoreByOther) ->
                aggregateByItem.put(itemId, PairScoreAggregate.of(scoreByOther)));
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
     * 내 챔피언이 애초에 거의 사지 않는 아이템은 후보에서 뺀다.
     * <p>
     * lift는 분모가 바닥이면 분자가 조금만 커도 폭발한다. 하한이 없을 때 실측하면
     * ally 상위 5 후보의 <b>96.9%가 구매율 1% 미만</b>이었고 lift 최대는 7749였다 —
     * counter에서 겪은 것과 같은 구조다. 그 상태에서는 {@code lift > 1} 문턱이
     * 아무것도 거르지 못한다.
     * <p>
     * 생성기와 feature 추출기가 <b>같은 조건</b>을 봐야 한다. 한쪽만 거르면 후보에 없는
     * 아이템이 ally feature만 갖거나 그 반대가 된다. 그래서 두 호출자가 공유하는
     * 이 클래스에 둔다.
     */
    private boolean belowBaseRateFloor(
            Long itemId, Map<Long, Integer> baseCountByItem, int baseGameCount) {
        if (minimumBaseRate <= 0.0 || baseGameCount == 0) {
            return false;
        }
        return (double) baseCountByItem.getOrDefault(itemId, 0) / baseGameCount < minimumBaseRate;
    }

    /**
     * 상대별 승률 lift — {@code P(win | 나, 상대, item) / P(win | 나, item)}.
     * <p>
     * 구매 lift가 "함께일 때 더 산다"(상관)라면 이것은 "함께 사면 더 이긴다"(시너지에 근접)다.
     * 추천 이유로만 쓰고 랭킹·feature에는 넣지 않는다 — 표본이 얇아 발견에 쓰기 어렵다.
     * <p>
     * 분모를 "이 아군과 함께일 때의 승률"이 아니라 "이 아이템의 평소 승률"로 둔 이유가 있다.
     * 전자는 아이템과 무관한 값이라 아이템 간 비교가 안 되고, 후자는 "이 아이템이 이 아군과
     * 있을 때 특별히 잘 되는가"를 직접 묻는다.
     * <p>
     * {@code minimumWinSamples} 미만은 아예 내지 않는다. 승패는 이항이라 표본이 얇으면
     * 승률이 0 아니면 1로 튄다
     */
    public Map<Long, Map<Long, Double>> winLiftsByItem(
            long myChampionId, List<Long> otherChampionIds, PairRelation relation,
            Map<Long, Double> itemWinRateById, double championWinRate, int minimumWinSamples
    ) {
        Map<Long, Map<Long, Double>> winLiftByItemAndOther = new HashMap<>();
        for (ChampionPairItemStats stats : findStats(myChampionId, otherChampionIds, relation)) {
            Double itemWinRate = itemWinRateById.get(stats.getItemId());
            if (itemWinRate == null || itemWinRate <= 0
                    || stats.getCoCountAll() < minimumWinSamples) {
                continue;
            }
            double pairBaselineLift = pairBaselineLift(stats, championWinRate);
            if (pairBaselineLift <= 0) {
                continue;
            }
            double pairItemWinRate = (double) stats.getWinCountAll() / stats.getCoCountAll();
            winLiftByItemAndOther
                    .computeIfAbsent(stats.getItemId(), itemId -> new HashMap<>())
                    .put(Long.valueOf(stats.getOtherChampionId()),
                            (pairItemWinRate / itemWinRate) / pairBaselineLift);
        }
        return winLiftByItemAndOther;
    }

    /**
     * {@code P(win | 나, 상대) / P(win | 나)} — 이 조합 자체가 평소보다 잘 이기는 정도.
     * 아이템과 무관한 값이라, 이것으로 나누면 상대의 강함이 상쇄된다.
     */
    private double pairBaselineLift(ChampionPairItemStats stats, double championWinRate) {
        if (championWinRate <= 0 || stats.getPairGameCountAll() <= 0) {
            return 0.0;
        }
        double pairWinRate = (double) stats.getPairWinCountAll() / stats.getPairGameCountAll();
        return pairWinRate / championWinRate;
    }

    /** {@code P(item | 나, 상대) / P(item | 나)}. counter와 같은 계산기를 쓴다. */
    private double lift(
            ChampionPairItemStats stats, Map<Long, Integer> baseCountByItem, int baseGameCount) {
        return pairLiftCalculator.calculate(
                stats.getCoCountAll(), stats.getPairGameCountAll(),
                baseCountByItem.getOrDefault(stats.getItemId(), 0), baseGameCount
        ).lift();
    }
}
