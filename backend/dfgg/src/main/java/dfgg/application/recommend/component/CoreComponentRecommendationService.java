package dfgg.application.recommend.component;

import dfgg.application.recommend.NextItemRecommendationService;
import dfgg.domain.match.ItemInventory;
import dfgg.infrastructure.external.dto.ItemResponse;
import dfgg.presentation.dto.RecommendedItemDto;
import dfgg.presentation.dto.request.NextItemRecommendationRequest;
import java.util.ArrayList;
import java.util.List;
import dfgg.application.recommend.context.CompositionFeatureCalculator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** 기존 Core 유스케이스의 Top-5와 명시적으로 제공된 현재 Inventory/예산을 연결한다. */
@Service
@ConditionalOnProperty(name = "recommendation.component-ltr.model-path")
public class CoreComponentRecommendationService {
    private final dfgg.application.champion.ChampionService champions;
    private final NextItemRecommendationService coreRecommendations;
    private final ComponentRecommendationService componentRecommendations;
    private final CompositionFeatureCalculator compositions;

    public CoreComponentRecommendationService(NextItemRecommendationService coreRecommendations,
            ComponentRecommendationService componentRecommendations, dfgg.application.champion.ChampionService champions, CompositionFeatureCalculator compositions) {
        this.compositions = compositions;
        this.champions = champions;
        this.coreRecommendations = coreRecommendations;
        this.componentRecommendations = componentRecommendations;
    }

    public List<TargetRecommendation> recommend(NextItemRecommendationRequest request, ItemInventory inventory,
            long availableGold, long gameTimeMs, ItemResponse catalog) {
        String version = catalog.dataVersion() == null ? catalog.version() : catalog.dataVersion();
        if (version == null || !version.matches("\\d+\\.\\d+\\.\\d+") || request.patch() == null
                || !request.patch().equals(version.substring(0, version.lastIndexOf('.')))) {
            throw new IllegalArgumentException("Core 요청과 Component 카탈로그의 명시적인 동일 패치가 필요합니다.");
        }
        var cores = coreRecommendations.recommendNextItem(request).recommendedItems();
        var targets = cores.stream().map(core -> Math.toIntExact(core.id())).toList();
        var allyIds = request.allies().stream().map(ally -> champions.findChampionByName(ally.name()).getChampionId()).toList();
        var enemyIds = request.enemies().stream().map(enemy -> champions.findChampionByName(enemy.name()).getChampionId()).toList();
        var composition = compositions.calculate(allyIds, enemyIds);
        var components = componentRecommendations.recommend(Math.toIntExact(champions.findChampionByName(request.myChampion().name()).getChampionId()), targets, inventory, availableGold, gameTimeMs,
                request.myChampion().position(), request.patch(), catalog, composition);
        var results = new ArrayList<TargetRecommendation>();
        for (int i = 0; i < cores.size(); i++) {
            results.add(new TargetRecommendation(cores.get(i), i + 1, components.get(i)));
        }
        return List.copyOf(results);
    }

    public record TargetRecommendation(RecommendedItemDto target, int coreRank,
                                       ComponentRecommendationService.TargetRecommendation component) {}
}
