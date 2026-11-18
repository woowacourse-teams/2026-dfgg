package dfgg.application.recommend;

import dfgg.application.champion.ChampionService;
import dfgg.application.counter.ChampionPositionReader;
import dfgg.application.recommend.component.ComponentPurchaseBundleMapper;
import dfgg.application.recommend.component.ComponentRecommendationQuery;
import dfgg.application.recommend.component.ComponentRecommendationRuntime;
import dfgg.application.recommend.component.ComponentRecommendationService;
import dfgg.application.recommend.context.CompositionFeatureCalculator;
import dfgg.application.recommend.situational.SituationalComponentRuntime;
import dfgg.application.recommend.situational.SituationalItemMapper;
import dfgg.application.recommend.v3.ReportedGameState;
import dfgg.domain.champion.Champion;
import dfgg.presentation.dto.SituationalItemDto;
import dfgg.common.exception.ChampionPositionNotFoundException;
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
import dfgg.presentation.dto.response.NextItemRecommendationResponse;
import dfgg.presentation.dto.response.NextItemRecommendationV4Response;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 기존 Core 추천을 유지하면서 현재 보유 상태로 목표별 구매 묶음을 연결한다. */
@Service
@Transactional(readOnly = true)
public class NextItemRecommendationV4Service {
    private static final Logger log = LoggerFactory.getLogger(NextItemRecommendationV4Service.class);
    static final String STAGE_METRIC = "recommendation.v4.stage";
    static final String EMPTY_CORE_METRIC = "recommendation.v4.empty_core";
    static final String V5_STAGE_METRIC = "recommendation.v5.stage";
    static final String V5_EMPTY_CORE_METRIC = "recommendation.v5.empty_core";
    static final String MODEL_TAG = "model";
    static final String GAME_SITUATION_MODEL = "game-situation";
    static final String BASELINE_MODEL = "baseline";

    private final NextItemRecommendationService coreRecommendations;
    private final ComponentRecommendationRuntime components;
    private final ChampionService champions;
    private final ChampionPositionReader positions;
    private final CompositionFeatureCalculator compositions;
    private final ComponentPurchaseBundleMapper bundles;
    private final LeagueOfLegendsVersionProperties version;
    private final SituationalComponentRuntime situational;
    private final SituationalItemMapper situationalItems;
    private final MeterRegistry meterRegistry;

    public NextItemRecommendationV4Service(NextItemRecommendationService coreRecommendations,
            ComponentRecommendationRuntime components, ChampionService champions, ChampionPositionReader positions,
            CompositionFeatureCalculator compositions, ComponentPurchaseBundleMapper bundles,
            LeagueOfLegendsVersionProperties version, SituationalComponentRuntime situational,
            SituationalItemMapper situationalItems, MeterRegistry meterRegistry) {
        this.coreRecommendations = coreRecommendations;
        this.components = components;
        this.champions = champions;
        this.positions = positions;
        this.compositions = compositions;
        this.bundles = bundles;
        this.version = version;
        this.situational = situational;
        this.situationalItems = situationalItems;
        this.meterRegistry = meterRegistry;
    }

    public NextItemRecommendationV4Response recommendNextItem(NextItemRecommendationV4Request request, Language language) {
        Meters meters = new Meters(STAGE_METRIC, EMPTY_CORE_METRIC, Tags.empty());
        return meters.stage("total").record(
                () -> recommend(request, language, meters, coreRecommendations::recommendNextItem));
    }

    /**
     * 새 추천 API가 타는 길. Core만 10명의 상태와 함께 추천받고, 나머지 흐름은 v4와 같다.
     * <p>
     * 지표는 v4와 다른 이름으로 남긴다. v4의 지표는 부하 측정이 읽으므로 새 API의 호출이 섞이면 안 된다.
     * 어느 모델이 순위를 매겼는지는 응답에 드러나지 않아 꼬리표로 남긴다.
     */
    public NextItemRecommendationV4Response recommendNextItem(
            NextItemRecommendationV4Request request, ReportedGameState state, Language language) {
        Meters meters = new Meters(V5_STAGE_METRIC, V5_EMPTY_CORE_METRIC, Tags.of(MODEL_TAG, rankingModel()));
        return meters.stage("total").record(() -> recommend(request, language, meters,
                (coreRequest, coreLanguage) -> coreRecommendations.recommendNextItem(coreRequest, state, coreLanguage)));
    }

    private String rankingModel() {
        if (coreRecommendations.ranksWithGameSituationModel()) {
            return GAME_SITUATION_MODEL;
        }
        return BASELINE_MODEL;
    }

    /** 부하 측정에서 어느 단계가 느린지 나누어 보도록 단계마다 이어지는 구간으로 시간을 잰다. */
    private NextItemRecommendationV4Response recommend(NextItemRecommendationV4Request requested, Language language,
            Meters meters, CoreRecommendation coreRecommendation) {
        Timer.Sample prepare = Timer.start(meterRegistry);
        String patch = operatingPatch(requested.patch());
        if (!components.isAvailable()) {
            throw new ComponentRecommendationUnavailableException();
        }
        NextItemRecommendationV4Request request = withMyPosition(requested);
        ItemResponse catalog = components.catalog();
        ItemInventory inventory = inventory(request, catalog);
        prepare.stop(meters.stage("prepare"));
        var coreRequest = new NextItemRecommendationRequest(request.myChampion(), request.purchasedItemIds(),
                request.allies(), request.enemies(), request.tier(), patch);
        var cores = meters.stage("core").record(() -> coreRecommendation.recommend(coreRequest, language).recommendedItems());
        var situationalItems = meters.stage("situational").record(() -> situationalItems(request, inventory, catalog, language));
        if (cores.isEmpty()) {
            meters.emptyCore().increment();
            return new NextItemRecommendationV4Response(List.of(), situationalItems);
        }
        Timer.Sample component = Timer.start(meterRegistry);
        var query = query(request, cores, inventory, patch);
        Map<Integer, ComponentRecommendationService.TargetRecommendation> byTarget = components.recommend(query).stream()
                .collect(Collectors.toMap(ComponentRecommendationService.TargetRecommendation::targetItemId, Function.identity()));
        var response = new NextItemRecommendationV4Response(cores.stream()
                .map(core -> RecommendedItemV4Dto.of(core, bundles.toDto(byTarget.get(Math.toIntExact(core.id())), catalog)))
                .toList(), situationalItems);
        component.stop(meters.stage("component"));
        return response;
    }

    /** 훈련모드에서 포지션을 비우거나 라인이 아닌 값으로 보내면 밴픽 상대 라인 예측과 같은 라인 분포에서 주 라인을 고른다. */
    private NextItemRecommendationV4Request withMyPosition(NextItemRecommendationV4Request request) {
        if (!request.isMyPositionUnknown()) {
            return request;
        }
        String name = request.myChampion().name();
        long championId = champions.findChampionByName(name).getChampionId();
        ChampionPosition position = positions.mainPositionOf(championId)
                .orElseThrow(() -> new ChampionPositionNotFoundException(name));
        return request.withMyPosition(position);
    }

    /** Core 추천을 받는 방법. 상태 없이 받는 v4와 상태와 함께 받는 새 API가 여기서만 갈린다. */
    @FunctionalInterface
    private interface CoreRecommendation {
        NextItemRecommendationResponse recommend(NextItemRecommendationRequest request, Language language);
    }

    /** 한 번의 추천이 지표를 남길 자리. API마다 이름과 꼬리표가 다르다. */
    private final class Meters {
        private final String stageMetric;
        private final String emptyCoreMetric;
        private final Tags tags;

        private Meters(String stageMetric, String emptyCoreMetric, Tags tags) {
            this.stageMetric = stageMetric;
            this.emptyCoreMetric = emptyCoreMetric;
            this.tags = tags;
        }

        private Timer stage(String name) {
            return meterRegistry.timer(stageMetric, tags.and("stage", name));
        }

        private Counter emptyCore() {
            return meterRegistry.counter(emptyCoreMetric, tags);
        }
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

    /**
     * Component 모델·조회표·카탈로그가 운영 패치 하나에 묶여 있으므로 요청 패치와 무관하게 운영 패치로 추천한다.
     * 새 패치 출시 직후처럼 클라이언트가 다른 패치를 보내도 거부하지 않고, 차이는 로그로만 남긴다.
     */
    private String operatingPatch(String requested) {
        String operating = version.version();
        if (requested != null && !requested.isBlank() && !operating.equals(requested)) {
            log.warn("v4 요청 패치가 운영 패치와 달라 운영 패치로 추천합니다: requested={}, operating={}", requested, operating);
        }
        return operating;
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
        ItemInventory inventory = ItemInventory.fromQuantities(quantities);
        // Core가 BOTTOM에 7개를 허용하는 근거와 같다 — 신발은 전용 슬롯에 있다고 본다.
        if (ChampionPosition.valueOf(request.myChampion().position()) == ChampionPosition.BOTTOM) {
            return inventory.moveBootsToQuestSlot();
        }
        return inventory;
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
