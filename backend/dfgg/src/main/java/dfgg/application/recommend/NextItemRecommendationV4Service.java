package dfgg.application.recommend;

import dfgg.application.champion.ChampionService;
import dfgg.application.recommend.component.ComponentPurchaseBundleMapper;
import dfgg.application.recommend.component.ComponentRecommendationQuery;
import dfgg.application.recommend.component.ComponentRecommendationRuntime;
import dfgg.application.recommend.component.ComponentRecommendationService;
import dfgg.application.recommend.context.CompositionFeatureCalculator;
import dfgg.application.recommend.situational.SituationalComponentRuntime;
import dfgg.application.recommend.situational.SituationalItemMapper;
import dfgg.domain.champion.Champion;
import dfgg.presentation.dto.SituationalItemDto;
import dfgg.common.exception.ComponentRecommendationUnavailableException;
import dfgg.common.exception.InvalidRecommendationRequestException;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.language.Language;
import dfgg.domain.match.ItemInventory;
import dfgg.infrastructure.config.LeagueOfLegendsVersionProperties;
import dfgg.infrastructure.external.catalog.DataDragonPurchaseItemCatalog;
import dfgg.infrastructure.external.dto.ItemResponse;
import dfgg.presentation.dto.ChampionDto;
import dfgg.presentation.dto.RecommendedItemDto;
import dfgg.presentation.dto.RecommendedItemV4Dto;
import dfgg.presentation.dto.request.NextItemRecommendationRequest;
import dfgg.presentation.dto.request.NextItemRecommendationV4Request;
import dfgg.presentation.dto.response.NextItemRecommendationV4Response;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 기존 Core 추천을 유지하면서 현재 보유 상태로 목표별 구매 묶음을 연결한다. */
@Service
@Transactional(readOnly = true)
public class NextItemRecommendationV4Service {
    private final NextItemRecommendationService coreRecommendations;
    private final ComponentRecommendationRuntime components;
    private final ChampionService champions;
    private final CompositionFeatureCalculator compositions;
    private final ComponentPurchaseBundleMapper bundles;
    private final LeagueOfLegendsVersionProperties version;
    private final SituationalComponentRuntime situational;
    private final SituationalItemMapper situationalItems;

    public NextItemRecommendationV4Service(NextItemRecommendationService coreRecommendations,
            ComponentRecommendationRuntime components, ChampionService champions,
            CompositionFeatureCalculator compositions, ComponentPurchaseBundleMapper bundles,
            LeagueOfLegendsVersionProperties version, SituationalComponentRuntime situational,
            SituationalItemMapper situationalItems) {
        this.coreRecommendations = coreRecommendations;
        this.components = components;
        this.champions = champions;
        this.compositions = compositions;
        this.bundles = bundles;
        this.version = version;
        this.situational = situational;
        this.situationalItems = situationalItems;
    }

    public NextItemRecommendationV4Response recommendNextItem(NextItemRecommendationV4Request request, Language language) {
        String patch = operatingPatch(request.patch());
        if (!components.isAvailable()) {
            throw new ComponentRecommendationUnavailableException();
        }
        ItemResponse catalog = components.catalog();
        ItemInventory inventory = inventory(request, catalog);
        var coreRequest = new NextItemRecommendationRequest(request.myChampion(), request.purchasedItemIds(),
                request.allies(), request.enemies(), request.tier(), patch);
        var cores = coreRecommendations.recommendNextItem(coreRequest, language).recommendedItems();
        var situationalItems = situationalItems(request, inventory, catalog, language);
        if (cores.isEmpty()) {
            return new NextItemRecommendationV4Response(List.of(), situationalItems);
        }
        var query = query(request, cores, inventory, patch);
        Map<Integer, ComponentRecommendationService.TargetRecommendation> byTarget = components.recommend(query).stream()
                .collect(Collectors.toMap(ComponentRecommendationService.TargetRecommendation::targetItemId, Function.identity()));
        return new NextItemRecommendationV4Response(cores.stream()
                .map(core -> RecommendedItemV4Dto.of(core, bundles.toDto(byTarget.get(Math.toIntExact(core.id())), catalog)))
                .toList(), situationalItems);
    }

    /** 상대 조합에 대한 추천이라 Core 목표와 무관하게 요청당 한 번 계산한다. */
    private List<SituationalItemDto> situationalItems(NextItemRecommendationV4Request request, ItemInventory inventory,
            ItemResponse catalog, Language language) {
        if (!situational.isAvailable()) return List.of();
        Map<Long, Champion> enemies = request.enemies().stream()
                .map(enemy -> champions.findChampionByName(enemy.name()))
                .collect(Collectors.toMap(Champion::getChampionId, Function.identity(), (first, second) -> first, LinkedHashMap::new));
        int championId = Math.toIntExact(champions.findChampionByName(request.myChampion().name()).getChampionId());
        var recommendations = situational.recommend(championId, ChampionPosition.valueOf(request.myChampion().position()).name(),
                enemies.keySet().stream().map(Math::toIntExact).toList(), inventory,
                request.purchasedItemIds().stream().map(Math::toIntExact).toList(), request.availableGold(), catalog);
        return situationalItems.toDtos(recommendations, catalog, enemies, language);
    }

    private String operatingPatch(String requested) {
        if (requested != null && !requested.isBlank() && !version.version().equals(requested)) {
            throw new InvalidRecommendationRequestException("현재 운영 패치만 추천할 수 있습니다: " + version.version());
        }
        return version.version();
    }

    private ItemInventory inventory(NextItemRecommendationV4Request request, ItemResponse catalog) {
        Map<Integer, Integer> quantities = request.inventoryItems().stream().collect(Collectors.toMap(
                item -> Math.toIntExact(item.itemId()), item -> item.quantity()));
        var purchaseCatalog = new DataDragonPurchaseItemCatalog(catalog.data());
        for (int id : quantities.keySet()) {
            if (purchaseCatalog.find(id).isEmpty()) {
                throw new InvalidRecommendationRequestException("보유 아이템을 운영 카탈로그에서 확인할 수 없습니다: " + id);
            }
        }
        return ItemInventory.fromQuantities(quantities);
    }

    private ComponentRecommendationQuery query(NextItemRecommendationV4Request request,
            List<RecommendedItemDto> cores, ItemInventory inventory, String patch) {
        var composition = compositions.calculate(championIds(request.allies()), championIds(request.enemies()));
        int championId = Math.toIntExact(champions.findChampionByName(request.myChampion().name()).getChampionId());
        return new ComponentRecommendationQuery(championId, cores.stream().map(core -> Math.toIntExact(core.id())).toList(),
                inventory, request.availableGold(), request.gameTimeMs(),
                ChampionPosition.valueOf(request.myChampion().position()), patch, composition);
    }

    private List<Long> championIds(List<ChampionDto> team) {
        return team.stream().map(champion -> champions.findChampionByName(champion.name()).getChampionId()).toList();
    }
}
