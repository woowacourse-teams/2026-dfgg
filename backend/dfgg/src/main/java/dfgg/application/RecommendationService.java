package dfgg.application;

import dfgg.application.champion.ChampionService;
import dfgg.common.CompositionStatsNotFoundException;
import dfgg.domain.stats.ChampionBuildStatsRepository;
import dfgg.domain.stats.CombinationContext;
import dfgg.domain.team.Team;
import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.item.Item;
import dfgg.domain.stats.ChampionBuildStats;
import dfgg.presentation.dto.ItemDto;
import dfgg.presentation.dto.request.RecommendationRequest;
import dfgg.presentation.dto.response.RecommendationResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class RecommendationService {

    private final ChampionService championService;
    private final ChampionBuildStatsRepository statsRepository;
    private final RecommendationBuildComposer buildComposer;

    public RecommendationService(
            ChampionService championService,
            ChampionBuildStatsRepository statsRepository,
            RecommendationBuildComposer buildComposer
    ) {
        this.championService = championService;
        this.statsRepository = statsRepository;
        this.buildComposer = buildComposer;
    }

    public RecommendationResponse recommend(RecommendationRequest request) {
        Champion myChampion = championService.findChampionByName(request.myChampion().name());
        ChampionPosition position = ChampionPosition.valueOf(request.myChampion().position());

        Team allies = new Team(request.allies().stream()
                .map(info -> championService.findChampionByName(info.name()))
                .toList());
        Team enemies = new Team(request.enemies().stream()
                .map(info -> championService.findChampionByName(info.name()))
                .toList());

        CombinationContext combinationContext = CombinationContext.analyze(enemies, allies);

        List<ChampionBuildStats> matchingStats = statsRepository.findAllMatchingStats(
                myChampion.getChampionId(),
                position.name(),
                combinationContext.enemyTankHeavy(),
                combinationContext.enemyApHeavy(),
                combinationContext.enemyAssassinHeavy(),
                combinationContext.allyHasMarksman(),
                combinationContext.allyTankHeavy()
        );
        if (matchingStats.isEmpty()) {
            throw new CompositionStatsNotFoundException(myChampion.getName(), position.name());
        }

        List<Item> bestItems = buildComposer.compose(matchingStats);
        if (bestItems.isEmpty()) {
            throw new CompositionStatsNotFoundException(myChampion.getName(), position.name());
        }
        List<ItemDto> itemDtos = bestItems.stream()
                .map(ItemDto::from)
                .toList();

        return new RecommendationResponse(
                myChampion.getName(),
                position.name(),
                itemDtos
        );
    }
}
