package dfgg.application;

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

    private final ChampionNameNormalizer championNameNormalizer;
    private final ChampionBuildStatsRepository statsRepository;

    public RecommendationService (ChampionNameNormalizer championNameNormalizer, ChampionBuildStatsRepository statsRepository) {
        this.championNameNormalizer = championNameNormalizer;
        this.statsRepository = statsRepository;
    }

    public RecommendationResponse recommend(RecommendationRequest request) {
        Champion myChampion = championNameNormalizer.normalize(request.myChampion().name());
        ChampionPosition position = ChampionPosition.valueOf(request.myChampion().position());

        Team allies = new Team(request.allies().stream()
                .map(info -> championNameNormalizer.normalize(info.name()))
                .toList());
        Team enemies = new Team(request.enemies().stream()
                .map(info -> championNameNormalizer.normalize(info.name()))
                .toList());

        CombinationContext combinationContext = CombinationContext.analyze(enemies, allies);

        ChampionBuildStats bestStats = statsRepository.findBestMatchingStats(
                myChampion.getChampionId(),
                position.name(),
                combinationContext.enemyTankHeavy(),
                combinationContext.enemyApHeavy(),
                combinationContext.enemyAssassinHeavy(),
                combinationContext.allyHasMarksman(),
                combinationContext.allyTankHeavy()
        ).orElseThrow(() -> new CompositionStatsNotFoundException(myChampion.getName(), position.name()));

        List<Item> bestItems = bestStats.getItems();
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
