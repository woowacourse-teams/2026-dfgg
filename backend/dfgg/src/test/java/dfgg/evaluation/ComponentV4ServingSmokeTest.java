package dfgg.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.application.champion.ChampionService;
import dfgg.application.recommend.NextItemRecommendationService;
import dfgg.application.recommend.NextItemRecommendationV4Service;
import dfgg.application.recommend.component.ComponentPurchaseBundleMapper;
import dfgg.application.recommend.component.ComponentRecommendationRuntime;
import dfgg.application.recommend.context.CompositionFeatureCalculator;
import dfgg.common.handler.GlobalExceptionHandler;
import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionRepository;
import dfgg.domain.champion.ChampionTag;
import dfgg.domain.image.ImageUrls;
import dfgg.domain.match.BuildPathResolver;
import dfgg.domain.match.ItemInventory;
import dfgg.infrastructure.config.ComponentLtrConfiguration;
import dfgg.infrastructure.config.ComponentRuntimeConfiguration;
import dfgg.infrastructure.config.LeagueOfLegendsVersionProperties;
import dfgg.infrastructure.external.catalog.DataDragonPurchaseItemCatalog;
import dfgg.presentation.NextItemRecommendationV4Controller;
import dfgg.presentation.dto.ChampionDto;
import dfgg.presentation.dto.RecommendedItemDto;
import dfgg.presentation.dto.request.InventoryItemRequest;
import dfgg.presentation.dto.request.NextItemRecommendationV4Request;
import dfgg.presentation.dto.response.NextItemRecommendationResponse;
import dfgg.presentation.dto.response.NextItemRecommendationV4Response;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;

/** 실제 모델·조회표·원격 카탈로그로 v4 HTTP 흐름을 검증한다. Core 추천과 챔피언 DB는 고정 입력이다. */
@Tag("evaluation")
class ComponentV4ServingSmokeTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<Long> TARGETS = List.of(3071L, 3031L, 3047L, 3869L, 3097L);

    @Test
    void trained_component_model_serves_v4_with_live_operating_catalog() throws Exception {
        Path model = Path.of(System.getProperty("evaluation.component.model")).toAbsolutePath();
        Path lookup = model.resolveSibling("component-policy-lookup.json");
        Path tags = model.resolveSibling("champion-tags.json");
        var modelJson = JSON.readTree(model.toFile());
        assertThat(sha256(tags)).isEqualTo(modelJson.path("champion_metadata_sha256").asText());
        String patch = JSON.readTree(lookup.toFile()).path("patch").asText();
        var champions = championFixtures(tags);
        var repository = mock(ChampionRepository.class);
        when(repository.findAllWithTagsByChampionIdIn(anyList())).thenAnswer(invocation -> {
            List<Long> ids = invocation.getArgument(0);
            return ids.stream().map(champions::get).toList();
        });
        var championService = mock(ChampionService.class);
        when(championService.findChampionByName(any())).thenAnswer(invocation ->
                champions.get(Long.parseLong(invocation.getArgument(0))));
        var core = mock(NextItemRecommendationService.class);
        when(core.recommendNextItem(any())).thenReturn(new NextItemRecommendationResponse(TARGETS.stream()
                .map(id -> new RecommendedItemDto(id, "Core fixture " + id, "fixture-image", null)).toList()));

        new ApplicationContextRunner()
                .withUserConfiguration(ComponentLtrConfiguration.class, ComponentRuntimeConfiguration.class)
                .withBean(LeagueOfLegendsVersionProperties.class, () -> new LeagueOfLegendsVersionProperties(patch))
                .withBean(RestClient.Builder.class, RestClient::builder)
                .withPropertyValues("recommendation.component-ltr.model-path=" + model.toUri(),
                        "recommendation.component-ltr.policy-lookup-path=" + lookup.toUri())
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var runtime = context.getBean(ComponentRecommendationRuntime.class);
                    assertThat(runtime.isAvailable()).as("실제 모델·조회표·원격 카탈로그 준비").isTrue();
                    var catalog = runtime.catalog();
                    var service = new NextItemRecommendationV4Service(core, runtime, championService,
                            new CompositionFeatureCalculator(repository),
                            new ComponentPurchaseBundleMapper(new ImageUrls("smoke-test", "ap-northeast-2", patch)),
                            new LeagueOfLegendsVersionProperties(patch));
                    var mvc = MockMvcBuilders.standaloneSetup(new NextItemRecommendationV4Controller(service))
                            .setControllerAdvice(new GlobalExceptionHandler()).build();
                    var cases = new ArrayList<Map<String, Object>>();
                    for (long gold : List.of(0L, 350L, 1000L, 1500L)) {
                        var request = request(gold);
                        String body = mvc.perform(post("/api/recommendations/v4").contentType(MediaType.APPLICATION_JSON)
                                        .content(JSON.writeValueAsBytes(request)))
                                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
                        var response = JSON.readValue(body, NextItemRecommendationV4Response.class);
                        assertThat(response.recommendedItems()).extracting(item -> item.id()).containsExactlyElementsOf(TARGETS);
                        int bundles = 0;
                        for (var recommended : response.recommendedItems()) {
                            var bundle = recommended.purchaseBundle();
                            if (bundle == null) continue;
                            bundles++;
                            assertThat(bundle.additionalCost()).isBetween(0L, gold);
                            var resolver = new BuildPathResolver(new DataDragonPurchaseItemCatalog(catalog.data()));
                            ItemInventory inventory = ItemInventory.fromQuantities(Map.of(1036, 1));
                            long cost = 0;
                            for (long id : bundle.purchaseOrder()) {
                                assertThat(id).isNotEqualTo(2055);
                                var purchase = resolver.singlePurchase(Math.toIntExact(id), inventory, gold - cost);
                                assertThat(purchase).isNotNull();
                                cost += purchase.additionalCost();
                                inventory = purchase.inventoryAfter();
                            }
                            assertThat(cost).isEqualTo(bundle.additionalCost());
                            for (var item : bundle.items()) {
                                var definition = catalog.data().get(item.itemId().toString());
                                assertThat(item.name()).isEqualTo(definition.name());
                                assertThat(definition.maps()).containsEntry("11", true);
                                assertThat(definition.gold().purchasable()).isTrue();
                                assertThat(definition.inStore()).isNotEqualTo(false);
                                assertThat(definition.hideFromAll()).isNotEqualTo(true);
                                assertThat(definition.consumed()).isNotEqualTo(true);
                                assertThat(definition.tags()).doesNotContain("Consumable", "Trinket");
                                assertThat(definition.image()).isNotNull();
                            }
                        }
                        if (gold > 0) assertThat(bundles).isPositive();
                        cases.add(Map.of("availableGold", gold, "bundleCount", bundles, "response", response));
                    }
                    Path output = Path.of("build/component-v4-serving-smoke/report.json");
                    Files.createDirectories(output.getParent());
                    JSON.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), Map.of(
                            "purpose", "SERVING_SMOKE_NOT_PRODUCTION_APPROVAL", "coreSource", "FIXED_TARGETS",
                            "modelSha256", sha256(model), "modelDeployable", modelJson.path("deployable").asBoolean(),
                            "lookupSha256", sha256(lookup), "championMetadataSha256", sha256(tags),
                            "dataVersion", catalog.dataVersion(), "cases", cases));
                    System.out.println("COMPONENT_V4_SMOKE " + output.toAbsolutePath());
                });
    }

    private Map<Long, Champion> championFixtures(Path tags) throws Exception {
        var byId = new HashMap<Long, List<ChampionTag>>();
        for (var row : JSON.readTree(tags.toFile())) {
            byId.computeIfAbsent(row.path("champion_id").asLong(), ignored -> new ArrayList<>())
                    .add(ChampionTag.valueOf(row.path("tag").asText()));
        }
        var champions = new HashMap<Long, Champion>();
        byId.forEach((id, championTags) -> champions.put(id,
                new Champion(id, id.toString(), Map.of("ko-KR", id.toString()), championTags)));
        return champions;
    }

    private NextItemRecommendationV4Request request(long gold) {
        var allies = IntStream.rangeClosed(1, 4).mapToObj(id -> new ChampionDto(Integer.toString(id), "TOP")).toList();
        var enemies = IntStream.rangeClosed(5, 9).mapToObj(id -> new ChampionDto(Integer.toString(id), "TOP")).toList();
        return new NextItemRecommendationV4Request(new ChampionDto("86", "TOP"), List.of(), allies, enemies,
                "EMERALD", null, List.of(new InventoryItemRequest(1036L, 1)), gold, 600_000L);
    }

    private String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
