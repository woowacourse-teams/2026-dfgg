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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * "이 아이템이 내 챔피언과 본질적으로 얼마나 잘 맞는가"로 후보를 찾는다.
 *
 * <p>Build와의 차이가 이 generator의 존재 이유다 — Build는 "지금 이 build에서 다음에 무엇을
 * 사는가"를 묻고, 여기서는 <b>구매 이력을 아예 보지 않는다</b>. 4코어까지 산 상황에서 Build가
 * 표본을 못 찾아도, 이 챔피언에게 잘 맞는 아이템은 여전히 후보로 올라와야 한다.
 *
 * <p>점수는 {@code P(item | champion, position)}의 Wilson 하한이다. 표본이 얇을수록 보수적으로
 * 깎이므로, 두세 판만 관측된 아이템이 100% 구매율로 상위를 차지하는 일이 없다.
 *
 * <p>태그 호환성은 여기서 점수에 섞지 않는다. 섞으려면 가중치가 필요한데, 그 가중치야말로
 * 이번 작업이 없애려는 수동 랭킹이다. 태그 정보는 feature extraction 단계에서 LTR에 따로 넘긴다.
 */
@Component
public class SelfSynergyCandidateGenerator implements CandidateGenerator {

    private final ChampionItemStatsRepository championItemStatsRepository;
    private final ChampionItemRollupRepository championItemRollupRepository;
    private final WilsonScoreCalculator wilsonScoreCalculator;

    /** 이 판수 미만이면 포지션 통계를 믿지 않고 챔피언 전체로 물러선다. */
    private final int minimumPositionSample;

    public SelfSynergyCandidateGenerator(
            ChampionItemStatsRepository championItemStatsRepository,
            ChampionItemRollupRepository championItemRollupRepository,
            WilsonScoreCalculator wilsonScoreCalculator,
            @Value("${recommendation.self-synergy.minimum-position-sample}") int minimumPositionSample
    ) {
        this.championItemStatsRepository = championItemStatsRepository;
        this.championItemRollupRepository = championItemRollupRepository;
        this.wilsonScoreCalculator = wilsonScoreCalculator;
        this.minimumPositionSample = minimumPositionSample;
    }

    @Override
    public CandidateSource source() {
        return CandidateSource.SELF_SYNERGY;
    }

    @Override
    public GeneratorResult generate(RecommendationQuery query, int topK) {
        int championId = Math.toIntExact(query.myChampionId());
        List<ChampionItemStats> positionStats =
                championItemStatsRepository.findByChampionIdAndPosition(championId, query.position());

        if (hasEnoughSample(positionStats)) {
            List<Compatibility> scores = positionStats.stream()
                    .map(stat -> new Compatibility(
                            stat.getItemId(),
                            wilson(stat.getPurchaseCountAll(), stat.getChampionGameCountAll()),
                            wilson(stat.getPurchaseCountRecent(), stat.getChampionGameCountRecent())))
                    .toList();
            return GeneratorResult.of(source(), unionOfTopK(scores, topK),
                    SelfSynergyBackoffLevel.CHAMPION_POSITION.ordinal());
        }

        List<ChampionItemRollup> rollup = championItemRollupRepository.findByChampionId(championId);
        List<Compatibility> scores = rollup.stream()
                .map(stat -> new Compatibility(
                        stat.getItemId(),
                        wilson(stat.getPurchaseCountAll(), stat.getChampionGameCountAll()),
                        wilson(stat.getPurchaseCountRecent(), stat.getChampionGameCountRecent())))
                .toList();
        return GeneratorResult.of(source(), unionOfTopK(scores, topK),
                SelfSynergyBackoffLevel.CHAMPION_ROLLUP.ordinal());
    }

    /**
     * 포지션 통계를 쓸 만한지 판단한다. 행이 있어도 그 포지션을 두세 판밖에 안 했다면
     * 구매율이 우연에 가까워, 포지션을 합친 쪽이 오히려 이 챔피언을 더 잘 설명한다.
     */
    private boolean hasEnoughSample(List<ChampionItemStats> positionStats) {
        return positionStats.stream()
                .mapToInt(ChampionItemStats::getChampionGameCountAll)
                .max()
                .orElse(0) >= minimumPositionSample;
    }

    private double wilson(int successes, int total) {
        return wilsonScoreCalculator.lowerBound(successes, total);
    }

    /**
     * 전체 기준 상위 K와 최근 기준 상위 K를 합친 뒤 둘 중 높은 점수로 정렬해 K개로 자른다.
     * Build와 같은 이유다 — 갓 버프된 아이템이 후보로조차 올라오지 못하면 랭커가 복구할 수 없다.
     */
    private List<ScoredItem> unionOfTopK(List<Compatibility> scores, int topK) {
        Set<Long> retrieved = new LinkedHashSet<>();
        retrieved.addAll(topKBy(scores, Compatibility::scoreAll, topK));
        retrieved.addAll(topKBy(scores, Compatibility::scoreRecent, topK));

        List<ScoredItem> ranked = new ArrayList<>();
        for (Compatibility score : scores) {
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

    private List<Long> topKBy(List<Compatibility> scores, ToDoubleFunction<Compatibility> scoreOf, int topK) {
        return scores.stream()
                .filter(score -> scoreOf.applyAsDouble(score) > 0.0)
                .sorted(Comparator.comparingDouble(scoreOf).reversed()
                        .thenComparing(Compatibility::itemId))
                .limit(topK)
                .map(Compatibility::itemId)
                .toList();
    }

    private record Compatibility(Long itemId, double scoreAll, double scoreRecent) {

        private double bestScore() {
            return Math.max(scoreAll, scoreRecent);
        }
    }
}
