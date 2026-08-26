package dfgg.application.recommend.fallback;

import dfgg.application.recommend.RecommendationBuildComposer;
import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionRepository;
import dfgg.domain.item.Item;
import dfgg.domain.stats.ChampionBuildStats;
import dfgg.domain.stats.ChampionBuildStatsRepository;
import dfgg.domain.stats.CombinationContext;
import dfgg.domain.team.Team;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 기존 {@code composition_stats} 기반 추천을 폴백 체인의 한 단계로 감싼다.
 * 새 경로(PrefixSpan+임베딩)가 답을 못 낼 때 이미 검증된 기존 로직에 맡기는 자리다.
 */
@Component
public class CompositionStatsStrategy implements RecommendationStrategy {

    private final ChampionRepository championRepository;
    private final ChampionBuildStatsRepository statsRepository;
    private final RecommendationBuildComposer buildComposer;

    public CompositionStatsStrategy(
            ChampionRepository championRepository,
            ChampionBuildStatsRepository statsRepository,
            RecommendationBuildComposer buildComposer
    ) {
        this.championRepository = championRepository;
        this.statsRepository = statsRepository;
        this.buildComposer = buildComposer;
    }

    @Override
    public FallbackStage stage() {
        return FallbackStage.COMPOSITION_STATS;
    }

    @Override
    public Optional<List<Long>> recommend(RecommendationContext context) {
        CombinationContext combinationContext = analyzeCombination(context);

        List<ChampionBuildStats> matchingStats = statsRepository.findAllMatchingStats(
                context.myChampionId(),
                context.position().name(),
                combinationContext.enemyTankHeavy(),
                combinationContext.enemyApHeavy(),
                combinationContext.enemyAssassinHeavy(),
                combinationContext.allyHasMarksman(),
                combinationContext.allyTankHeavy()
        );
        if (matchingStats.isEmpty()) {
            return Optional.empty();
        }

        List<Long> composedBuildItemIds = buildComposer.compose(matchingStats, context.position()).stream()
                .map(Item::getItemId)
                .toList();
        return NextItemSelector.pick(composedBuildItemIds, context.purchasedItemIds().size());
    }

    private CombinationContext analyzeCombination(RecommendationContext context) {
        List<Champion> allies = championRepository.findAllById(context.allyChampionIds());
        List<Champion> enemies = championRepository.findAllById(context.enemyChampionIds());
        return CombinationContext.analyze(new Team(enemies), new Team(allies));
    }
}
