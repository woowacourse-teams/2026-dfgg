package dfgg.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.application.recommend.component.ComponentRecommendationRuntime;
import dfgg.domain.image.ImageUrls;
import dfgg.domain.match.BuildPathResolver;
import dfgg.domain.match.ItemInventory;
import dfgg.infrastructure.external.catalog.DataDragonPurchaseItemCatalog;
import dfgg.presentation.dto.ChampionDto;
import dfgg.presentation.dto.RecommendedItemDto;
import dfgg.presentation.dto.request.InventoryItemRequest;
import dfgg.presentation.dto.request.NextItemRecommendationV4Request;
import dfgg.presentation.dto.response.NextItemRecommendationResponse;
import dfgg.presentation.dto.response.NextItemRecommendationV4Response;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 실제 통계 DB·모델·원격 카탈로그를 사용하는 HTTP 계약 검증이며 추천 품질 평가는 아니다. */
@Tag("evaluation")
@ActiveProfiles("evaluation")
@SpringBootTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=none",
        "spring.datasource.hikari.read-only=true", "collection.scheduler.enabled=false", "riot.version=16.18"})
@AutoConfigureMockMvc
@Transactional(readOnly = true)
class ComponentV4DatabaseServingTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<Integer> ENEMIES = List.of(266, 104, 238, 51, 412);

    @Autowired MockMvc mvc;
    @Autowired ComponentRecommendationRuntime runtime;
    @Autowired ImageUrls images;
    @Autowired JdbcTemplate jdbc;
    @Autowired Environment environment;
    @Autowired ResourceLoader resources;

    @Test
    void real_core_and_component_models_preserve_v3_and_serve_executable_bundles() throws Exception {
        assertThat(jdbc.queryForObject("show transaction_read_only", String.class)).isEqualTo("on");
        assertThat(runtime.isAvailable()).as("모델·조회표·실제 Data Dragon 카탈로그 준비").isTrue();
        Map<String, Object> provenance = provenance();
        var cases = new ArrayList<Map<String, Object>>();
        for (Scenario scenario : scenarios()) {
            var request = request(scenario);
            var before = call("v3", request.toCoreRequest(), NextItemRecommendationResponse.class);
            var response = call("v4", request, NextItemRecommendationV4Response.class);
            var after = call("v3", request.toCoreRequest(), NextItemRecommendationResponse.class);
            assertThat(before.recommendedItems()).as(scenario.name()).isNotEmpty().hasSizeLessThanOrEqualTo(5);
            assertThat(after).as("v4 호출 후에도 v3 결과 유지").isEqualTo(before);
            assertThat(response.recommendedItems().stream().map(item ->
                    new RecommendedItemDto(item.id(), item.name(), item.imageUrl(), item.description())).toList())
                    .as("Core 순서·이름·이미지·설명 유지: " + scenario.name())
                    .containsExactlyElementsOf(before.recommendedItems());
            int bundles = verifyBundles(scenario, response);
            if (scenario.gold() == 0) {
                assertThat(bundles).isZero();
            } else {
                assertThat(bundles).as("실제 Component 구매 묶음: " + scenario.name()).isPositive();
            }
            cases.add(Map.of("scenario", scenario.name(), "request", request,
                    "response", response, "bundleCount", bundles, "v3Unchanged", true));
        }
        assertThat(provenance()).as("실행 중 모델·조회표·카탈로그·통계 불변").isEqualTo(provenance);
        Path output = Path.of("build/component-v4-database-serving/report.json");
        Files.createDirectories(output.getParent());
        JSON.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), Map.of("provenance", provenance, "cases", cases));
        System.out.println("COMPONENT_V4_DATABASE_SERVING " + output.toAbsolutePath());
    }

    private int verifyBundles(Scenario scenario, NextItemRecommendationV4Response response) {
        var catalog = runtime.catalog();
        var resolver = new BuildPathResolver(new DataDragonPurchaseItemCatalog(catalog.data()));
        int bundles = 0;
        for (var item : response.recommendedItems()) {
            var bundle = item.purchaseBundle();
            if (bundle == null) continue;
            bundles++;
            assertThat(bundle.additionalCost()).isBetween(0L, scenario.gold());
            assertThat(bundle.purchaseOrder()).isNotEmpty().doesNotContain(2055L);
            // 각 Core 대안은 같은 최초 인벤토리와 예산에서 독립적으로 실행 가능해야 한다.
            var inventory = ItemInventory.fromQuantities(scenario.inventory());
            long cost = 0;
            for (long id : bundle.purchaseOrder()) {
                var purchase = resolver.singlePurchase(Math.toIntExact(id), inventory, scenario.gold() - cost);
                assertThat(purchase).as("실행 가능한 구매 순서: " + scenario.name()).isNotNull();
                cost += purchase.additionalCost();
                inventory = purchase.inventoryAfter();
            }
            assertThat(cost).isEqualTo(bundle.additionalCost());
            for (var purchaseItem : bundle.items()) {
                assertThat(purchaseItem.name()).isEqualTo(catalog.data().get(purchaseItem.itemId().toString()).name());
                assertThat(purchaseItem.imageUrl()).isEqualTo(images.itemOf(purchaseItem.itemId()));
                assertThat(bundle.purchaseOrder().stream().filter(id -> id.equals(purchaseItem.itemId())).count())
                        .isEqualTo(purchaseItem.quantity());
            }
        }
        return bundles;
    }

    private List<Scenario> scenarios() {
        return List.of(
                new Scenario("standard", 1100, Map.of(1055, 1), ENEMIES, List.of()),
                new Scenario("no-gold", 0, Map.of(1055, 1), ENEMIES, List.of()),
                new Scenario("tank-enemies", 1100, Map.of(1055, 1), List.of(54, 33, 516, 12, 113), List.of()),
                new Scenario("mage-enemies", 1100, Map.of(1055, 1), List.of(1, 63, 99, 112, 45), List.of()),
                new Scenario("owned-materials", 1100, Map.of(1055, 1, 3057, 1, 1036, 1), ENEMIES, List.of()),
                new Scenario("boots-in-inventory", 1100, Map.of(1055, 1, 1001, 1), ENEMIES, List.of()),
                new Scenario("after-first-core", 1100, Map.of(3078, 1, 1001, 1), ENEMIES, List.of(3078L)));
    }

    private NextItemRecommendationV4Request request(Scenario scenario) {
        var allies = List.of(86, 64, 103, 89);
        var allyRoles = List.of("TOP", "JUNGLE", "MID", "SUPPORT");
        var enemyRoles = List.of("TOP", "JUNGLE", "MID", "BOTTOM", "SUPPORT");
        return new NextItemRecommendationV4Request(champion(81, "BOTTOM"), scenario.purchasedCoreIds(),
                IntStream.range(0, 4).mapToObj(i -> champion(allies.get(i), allyRoles.get(i))).toList(),
                IntStream.range(0, 5).mapToObj(i -> champion(scenario.enemies().get(i), enemyRoles.get(i))).toList(),
                null, null, scenario.inventory().entrySet().stream().sorted(Map.Entry.comparingByKey())
                        .map(entry -> new InventoryItemRequest(entry.getKey().longValue(), entry.getValue())).toList(),
                scenario.gold(), 480_000L);
    }

    private ChampionDto champion(int id, String position) {
        String name = jdbc.queryForObject("select name->>'en-US' from champions where champion_id=?", String.class, id);
        assertThat(name).isNotBlank();
        return new ChampionDto(name, position);
    }

    private <T> T call(String version, Object request, Class<T> responseType) throws Exception {
        String body = mvc.perform(post("/api/recommendations/" + version).contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsBytes(request)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JSON.readValue(body, responseType);
    }

    private Map<String, Object> provenance() throws Exception {
        byte[] core = resourceBytes("recommendation.ltr.model-path");
        byte[] component = resourceBytes("recommendation.component-ltr.model-path");
        byte[] lookup = resourceBytes("recommendation.component-ltr.preparation-lookup-path");
        var model = JSON.readTree(component);
        String championHash = hash(JSON.writeValueAsBytes(jdbc.queryForList(
                "select c.champion_id, t.tag from champions c left join champion_tags t on t.champion_id=c.champion_id order by c.champion_id, t.tag")));
        assertThat(championHash).isEqualTo(model.path("champion_metadata_sha256").asText());
        assertThat(hash(lookup)).isEqualTo(model.path("preparation_lookup_sha256").asText());
        var result = new LinkedHashMap<String, Object>();
        result.put("purpose", "HTTP_DATABASE_SMOKE_NOT_PRODUCTION_APPROVAL");
        result.put("coreModelSha256", hash(core));
        result.put("componentModelSha256", hash(component));
        result.put("modelDeployable", model.path("deployable").asBoolean());
        result.put("lookupSha256", hash(lookup));
        result.put("championMetadataSha256", championHash);
        result.put("dataVersion", runtime.catalog().dataVersion());
        result.put("catalogSha256", hash(JSON.writeValueAsBytes(runtime.catalog())));
        result.put("statisticsSnapshot", jdbc.queryForList(
                "select count(*) as samples, count(distinct match_id) as matches, max(id) as max_id from composition_stats_samples"));
        return result;
    }

    private byte[] resourceBytes(String property) throws Exception {
        try (var input = resources.getResource(environment.getRequiredProperty(property)).getInputStream()) {
            return input.readAllBytes();
        }
    }

    private String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private record Scenario(String name, long gold, Map<Integer, Integer> inventory,
                            List<Integer> enemies, List<Long> purchasedCoreIds) {}
}
