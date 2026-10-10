package dfgg.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.application.ChampionPositionNormalizer;
import dfgg.application.champion.ChampionService;
import dfgg.application.match.ParticipantItemPurchaseBundle;
import dfgg.application.match.ParticipantItemPurchaseSnapshot;
import dfgg.application.match.ParticipantItemPurchaseSnapshotService;
import dfgg.application.match.PurchaseInventoryReconstructor;
import dfgg.application.recommend.component.ComponentRecommendationService;
import dfgg.application.recommend.component.CoreComponentRecommendationService;
import dfgg.application.recommend.context.CompositionFeatureCalculator;
import dfgg.domain.match.ItemInventory;
import dfgg.domain.match.ItemPurchaseType;
import dfgg.domain.match.NormalizedMatchParticipant;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import dfgg.domain.match.PreparationLookup;
import dfgg.infrastructure.external.catalog.DataDragonPurchaseItemCatalog;
import dfgg.infrastructure.external.dto.ItemResponse;
import dfgg.presentation.dto.ChampionDto;
import dfgg.presentation.dto.request.NextItemRecommendationRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** 고정 모델/통계를 읽고 실제 유스케이스를 호출한다. 평가를 위해 추천 로직을 복제하지 않는다. */
@Tag("evaluation")
@ActiveProfiles("evaluation")
@SpringBootTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=none",
        "spring.datasource.hikari.read-only=true", "collection.scheduler.enabled=false"})
@Transactional(readOnly = true)
class ComponentEvaluationFrameworkTest {
    @Autowired CoreComponentRecommendationService serving;
    @Autowired ComponentRecommendationService components;
    @Autowired PreparationLookup lookup;
    @Autowired CompositionFeatureCalculator compositions;
    @Autowired ChampionService champions;
    @Autowired ParticipantItemPurchaseSnapshotService snapshots;
    @Autowired PurchaseInventoryReconstructor reconstructor;
    @Autowired NormalizedMatchParticipantRepository participants;
    @Autowired JdbcTemplate jdbc;
    @Autowired Environment environment;
    @Autowired ResourceLoader resources;

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<Integer, String> championNames = new HashMap<>();
    private ItemResponse response;
    private DataDragonPurchaseItemCatalog catalog;
    private String patch;
    private Path output;

    @Test
    void evaluate_frozen_pipeline_and_mock_scenarios() throws Exception {
        output = Path.of(required("evaluation.framework.output"));
        assertThat(Files.exists(output.resolve("replay-results.jsonl"))).as("기존 평가 보존").isFalse();
        response = mapper.readValue(Path.of(required("evaluation.framework.catalog")).toFile(), ItemResponse.class);
        String version = response.dataVersion() == null ? response.version() : response.dataVersion();
        patch = version.substring(0, version.lastIndexOf('.'));
        catalog = new DataDragonPurchaseItemCatalog(response.data());
        assertThat(lookup.enabled()).as("Preparation 경로를 반드시 실행한다").isTrue();
        lookup.validatePatch(patch);
        Map<String, Object> provenance = provenance();
        mockScenarios();
        if (!Boolean.getBoolean("evaluation.framework.mock-only")) replay();
        assertThat(provenance()).as("실행 중 모델·Lookup·카탈로그 불변").isEqualTo(provenance);
        write("serving-metadata.json", provenance);
    }

    private Map<String, Object> provenance() throws Exception {
        byte[] core = resourceBytes("recommendation.ltr.model-path");
        byte[] component = resourceBytes("recommendation.component-ltr.model-path");
        assertThat(mapper.readTree(core).path("objective").asText()).isEqualTo("lambdarank");
        assertThat(mapper.readTree(component).path("feature_names").size()).isEqualTo(59);
        String lookupHash = hash(resourceBytes("recommendation.component-ltr.preparation-lookup-path"));
        assertThat(lookupHash).isEqualTo(lookup.sha256());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("purpose", "PRODUCTION_SNAPSHOT_DIAGNOSTIC_NOT_INDEPENDENT_HOLDOUT");
        result.put("patch", patch);
        result.put("coreModelSha256", hash(core));
        result.put("componentModelSha256", hash(component));
        result.put("lookupSha256", lookupHash);
        result.put("catalogSha256", hash(Files.readAllBytes(Path.of(required("evaluation.framework.catalog")))));
        result.put("championMetadataSha256", hash(mapper.writeValueAsBytes(jdbc.queryForList(
                "select c.champion_id, t.tag from champions c left join champion_tags t on t.champion_id=c.champion_id order by c.champion_id, t.tag"))));
        result.put("replayInputSha256", hash(Files.readAllBytes(output.resolve("replay-input.jsonl"))));
        result.put("inputPolicy", "RESTORED_INVENTORY_AND_GOLD_LOWER; PAST_OWNED_CORE_OR_BOOTS; ALL_REPLAYABLE_STAGES");
        result.put("coreScore", "NOT_EXPOSED_BY_SERVING_RESPONSE");
        result.put("statisticsSnapshot", jdbc.queryForList("select count(*) as samples, count(distinct match_id) as matches, max(id) as max_id from composition_stats_samples"));
        return result;
    }

    private void mockScenarios() throws Exception {
        var allies = List.of(86, 64, 103, 89);
        var ordinary = List.of(266, 104, 238, 51, 412);
        var scenarios = List.of(
                new Scenario("preparation_ezreal", 1100, Map.of(1055, 1), ordinary),
                new Scenario("enemy_tank_heavy", 1100, Map.of(1055, 1), List.of(54, 33, 516, 12, 113)),
                new Scenario("enemy_mage_heavy", 1100, Map.of(1055, 1), List.of(1, 63, 99, 112, 45)),
                new Scenario("existing_materials", 1100, Map.of(1055, 1, 3057, 1, 1036, 1), ordinary),
                new Scenario("low_gold", 100, Map.of(1055, 1), ordinary));
        var results = new ArrayList<Map<String, Object>>();
        for (var scenario : scenarios) {
            var request = request(81, "BOTTOM", List.of(), allies, scenario.enemies(), null);
            ItemInventory inventory = inventory(scenario.inventory());
            var composition = compositions.calculate(longs(allies), longs(scenario.enemies()));
            var predictions = serving.recommend(request, inventory, scenario.gold(), 480_000, response);
            assertThat(predictions).hasSize(5);
            var branches = new ArrayList<Map<String, Object>>();
            int preparationCandidates = 0;
            int emptyBranches = 0;
            for (var prediction : predictions) {
                var branch = prediction.component();
                assertThat(branch.targetItemId()).isEqualTo(prediction.target().id().intValue());
                var independently = components.recommend(81, List.of(branch.targetItemId()), inventory,
                        scenario.gold(), 480_000, "BOTTOM", patch, response, composition).getFirst();
                assertThat(branch).as("Target branch 독립 실행").isEqualTo(independently);
                assertThat(branch.status()).isIn(ComponentRecommendationService.Status.OK, ComponentRecommendationService.Status.NO_CANDIDATE,
                        ComponentRecommendationService.Status.UNSUPPORTED_TARGET_MECHANIC, ComponentRecommendationService.Status.UNSUPPORTED_TARGET_TYPE);
                if (branch.rankedPurchases().isEmpty()) emptyBranches++;
                var signatures = new HashSet<Map<Integer, Integer>>();
                for (var purchase : branch.rankedPurchases()) {
                    assertThat(signatures.add(purchase.candidate().itemQuantities())).isTrue();
                    verifyPurchase(branch.targetItemId(), inventory, purchase);
                    assertThat(purchase.additionalCost()).isBetween(0L, scenario.gold());
                    assertThat(purchase.candidate().features()).hasSize(59);
                    assertThat(purchase.candidate().features().subList(46, 55)).isEqualTo(composition.values());
                    assertThat(purchase.candidate().features().get(4)).isLessThanOrEqualTo(6.0);
                    purchase.candidate().itemQuantities().keySet().forEach(id -> assertThat(catalog.find(id)).isPresent());
                    if (purchase.candidateSource() == dfgg.domain.match.ComponentCandidateGenerator.Source.PREPARATION) {
                        preparationCandidates++;
                        assertThat(purchase.candidate().itemQuantities()).hasSize(1);
                        int id = purchase.candidate().itemQuantities().keySet().iterator().next();
                        assertThat(inventory.quantityOf(id)).isZero();
                        assertThat(lookup.find(81, "BOTTOM", branch.targetItemId()).stream().map(PreparationLookup.Pattern::itemId)).contains(id);
                    }
                }
                branches.add(branchRow(prediction));
            }
            if (scenario.name().equals("preparation_ezreal")) assertThat(preparationCandidates).isPositive();
            if (scenario.name().equals("low_gold")) assertThat(emptyBranches).isEqualTo(predictions.size());
            if (scenario.name().equals("enemy_tank_heavy")) assertThat(composition.enemyTankCount()).isGreaterThanOrEqualTo(3);
            if (scenario.name().equals("enemy_mage_heavy")) assertThat(composition.enemyMageCount()).isGreaterThanOrEqualTo(3);
            results.add(Map.of("name", scenario.name(), "input", Map.of("request", request, "championId", 81,
                    "inventory", scenario.inventory(), "gold", scenario.gold(), "gameTimeMs", 480_000, "composition", composition),
                    "branches", branches, "checks", Map.of("passed", true, "branchIndependence", true, "affordability", true,
                            "catalogAndSlots", true, "compositionFeatures", true, "preparationCandidates", preparationCandidates, "emptyBranches", emptyBranches)));
        }
        write("mock-serving-results.json", Map.of("accuracyApplicable", false, "scenarios", results));
        System.out.println("FRAMEWORK_MOCK scenarios=" + results.size());
    }

    /** 후보의 구매 순서를 다시 실행해 남은 레시피, 비용, 실제 보유 상태를 검증한다. */
    private void verifyPurchase(int target, ItemInventory before, ComponentRecommendationService.ScoredPurchase purchase) {
        var matcher = new dfgg.domain.match.PurchaseRecipeMatcher(catalog);
        var resolver = new dfgg.domain.match.BuildPathResolver(catalog);
        ItemInventory after = before;
        long cost = 0;
        for (int id : purchase.candidate().purchaseOrder()) {
            if (purchase.candidateSource() == dfgg.domain.match.ComponentCandidateGenerator.Source.TARGET_PATH) {
                assertThat(matcher.remainingPath(catalog.find(target).orElseThrow(), after).stream()
                        .map(dfgg.domain.match.PurchaseCatalogItem::id)).as("이미 채워진 레시피 위치를 중복 구매하지 않는다").contains(id);
            }
            var step = resolver.singlePurchase(id, after, Long.MAX_VALUE);
            assertThat(step).isNotNull();
            cost += step.additionalCost();
            after = step.inventoryAfter();
        }
        assertThat(cost).isEqualTo(purchase.additionalCost());
        assertThat(after.quantities()).isEqualTo(purchase.candidate().inventoryAfter());
        assertThat(catalog.occupiedSlots(after)).isLessThanOrEqualTo(6);
    }

    private Map<String, Object> branchRow(CoreComponentRecommendationService.TargetRecommendation prediction) {
        var branch = prediction.component();
        var row = new LinkedHashMap<String, Object>();
        row.put("coreRank", prediction.coreRank());
        row.put("targetItemId", branch.targetItemId());
        row.put("targetName", prediction.target().name());
        row.put("status", branch.status().name());
        row.put("reason", branch.reason());
        row.put("candidateCount", branch.rankedPurchases().size());
        row.put("preparationCandidateCount", branch.rankedPurchases().stream().filter(p -> p.candidateSource() == dfgg.domain.match.ComponentCandidateGenerator.Source.PREPARATION).count());
        row.put("top3", branch.rankedPurchases().stream().limit(3).map(p -> Map.of(
                "rank", p.rankWithinTarget(), "items", namedItems(p.candidate().itemQuantities()), "itemQuantities", p.candidate().itemQuantities(),
                "source", p.candidateSource().name(), "score", p.score(), "cost", p.additionalCost(), "features", p.candidate().features())).toList());
        return row;
    }

    private Map<String, Integer> namedItems(Map<Integer, Integer> quantities) {
        Map<String, Integer> named = new TreeMap<>();
        quantities.forEach((id, quantity) -> named.put(response.data().get(id.toString()).name() + " (" + id + ")", quantity));
        return named;
    }

    private void replay() throws Exception {
        Map<String, List<JsonNode>> byMatch = new TreeMap<>();
        try (var lines = Files.lines(output.resolve("replay-input.jsonl"))) {
            var iterator = lines.iterator();
            while (iterator.hasNext()) {
                var row = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(iterator.next());
                row.remove(List.of("candidates", "featureNames"));
                byMatch.computeIfAbsent(row.path("matchId").asText(), ignored -> new ArrayList<>()).add(row);
            }
        }
        Set<String> events = new HashSet<>(jdbc.queryForList("select distinct match_id from normalized_match_participant_item_events", String.class));
        Set<String> inStats = new HashSet<>(jdbc.queryForList("select distinct match_id from composition_stats_samples", String.class));
        int done = 0;
        try (var writer = Files.newBufferedWriter(output.resolve("replay-results.jsonl"))) {
            for (var entry : byMatch.entrySet()) {
                List<NormalizedMatchParticipant> team = events.contains(entry.getKey()) ? participants.findByMatchId(entry.getKey()) : List.of();
                var bundles = team.size() == 10 ? snapshots.bundlesForMatch(entry.getKey()) : List.<ParticipantItemPurchaseBundle>of();
                var purchases = team.size() == 10 ? snapshots.snapshotsForMatch(entry.getKey()) : List.<ParticipantItemPurchaseSnapshot>of();
                for (var label : entry.getValue()) {
                    var result = identity(label);
                    result.put("statisticsContainsMatch", inStats.contains(entry.getKey()));
                    if (!events.contains(entry.getKey())) exclude(result, "REPLAY_EVENTS_MISSING");
                    else if (team.size() != 10) exclude(result, "MATCH_PARTICIPANTS_MISSING");
                    else evaluate(label, bundles, purchases, team, result);
                    writer.write(mapper.writeValueAsString(result) + "\n");
                }
                writer.flush();
                if (++done % 25 == 0) System.out.println("FRAMEWORK_REPLAY matches=" + done + "/" + byMatch.size());
            }
        }
    }

    private void evaluate(JsonNode label, List<ParticipantItemPurchaseBundle> bundles,
            List<ParticipantItemPurchaseSnapshot> purchases, List<NormalizedMatchParticipant> team, Map<String, Object> row) {
        int participantId = label.path("participantId").asInt();
        int order = label.path("startPurchaseOrder").asInt();
        var bundle = bundles.stream().filter(b -> b.participantId() == participantId && b.startPurchaseOrder() == order).findFirst().orElse(null);
        if (bundle == null || !bundle.ready()) { exclude(row, "BUNDLE_NOT_REPLAYABLE"); return; }
        if (!mapper.valueToTree(bundle.inventoryBefore().quantities()).equals(label.path("inventoryBefore"))
                || bundle.goldLower() != label.path("goldLower").asInt() || bundle.goldUpper() != label.path("goldUpper").asInt()
                || bundle.endPurchaseOrder() != label.path("endPurchaseOrder").asInt()
                || !mapper.valueToTree(bundle.itemQuantities()).equals(label.path("observedItems"))) {
            exclude(row, "SNAPSHOT_DATASET_MISMATCH"); return;
        }
        final NextItemRecommendationRequest request;
        try {
            request = replayRequest(bundle, purchases, team);
        } catch (IllegalArgumentException exception) {
            exclude(row, "REQUEST_NOT_RECONSTRUCTABLE");
            row.put("error", exception.getMessage());
            return;
        }
        row.put("status", "EVALUATED");
        row.put("reason", "CORE_MISS");
        try {
            // 정답 Target/observedItems는 이 호출의 입력에 포함되지 않는다.
            var predictions = serving.recommend(request, bundle.inventoryBefore(), bundle.goldLower(),
                    bundle.purchases().getFirst().sourceEvent().gameTimeMs(), response);
            row.put("predictedCoreIds", predictions.stream().map(p -> p.target().id()).toList());
            int target = label.path("targetItemId").asInt();
            var observed = quantities(label.path("observedItems"));
            for (var prediction : predictions) {
                if (prediction.component().targetItemId() != target) continue;
                row.put("observedCoreRank", prediction.coreRank());
                row.put("branchStatus", prediction.component().status().name());
                row.put("candidateCount", prediction.component().rankedPurchases().size());
                row.put("reason", prediction.component().status() == ComponentRecommendationService.Status.OK ? "CANDIDATE_MISS" : prediction.component().status().name());
                for (var purchase : prediction.component().rankedPurchases()) {
                    if (!purchase.candidate().itemQuantities().equals(observed)) continue;
                    row.put("observedComponentRank", purchase.rankWithinTarget());
                    row.put("positiveSource", purchase.candidateSource().name());
                    row.put("reason", purchase.rankWithinTarget() == 1 ? "SUCCESS" : "COMPONENT_RANK_MISS");
                }
            }
        } catch (RuntimeException exception) {
            row.put("status", "INFERENCE_FAILED");
            row.put("reason", "SERVING_INFERENCE_FAILED");
            row.put("error", exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    private NextItemRecommendationRequest replayRequest(ParticipantItemPurchaseBundle bundle,
            List<ParticipantItemPurchaseSnapshot> purchases, List<NormalizedMatchParticipant> team) {
        var me = team.stream().filter(p -> p.getParticipantId() == bundle.participantId()).findFirst().orElseThrow();
        String position = new ChampionPositionNormalizer().normalize(me.getPosition()).orElseThrow().name();
        var past = purchases.stream().filter(p -> p.purchase().getParticipantId() == bundle.participantId())
                .filter(p -> p.purchase().getPurchaseOrder() < bundle.startPurchaseOrder())
                .filter(p -> p.purchase().getPurchaseType() == ItemPurchaseType.CORE || p.purchase().getPurchaseType() == ItemPurchaseType.BOOTS)
                .filter(p -> reconstructor.isStillOwned(p.purchase().getItemId(), bundle.inventoryBefore(), patch))
                .map(p -> Long.valueOf(p.purchase().getItemId())).distinct().toList();
        var allies = team.stream().filter(p -> !p.getParticipantId().equals(me.getParticipantId()) && p.getTeamId().equals(me.getTeamId())).map(this::participantChampion).toList();
        var enemies = team.stream().filter(p -> !p.getTeamId().equals(me.getTeamId())).map(this::participantChampion).toList();
        return new NextItemRecommendationRequest(champion(me.getChampionId(), position), past, allies, enemies, me.getTier(), patch);
    }

    private NextItemRecommendationRequest request(int champion, String role, List<Long> past, List<Integer> allies, List<Integer> enemies, String tier) {
        return new NextItemRecommendationRequest(champion(champion, role), past,
                java.util.stream.IntStream.range(0, allies.size()).mapToObj(i -> champion(allies.get(i), List.of("TOP", "JUNGLE", "MID", "SUPPORT").get(i))).toList(),
                java.util.stream.IntStream.range(0, enemies.size()).mapToObj(i -> champion(enemies.get(i), List.of("TOP", "JUNGLE", "MID", "BOTTOM", "SUPPORT").get(i))).toList(), tier, patch);
    }

    private ChampionDto participantChampion(NormalizedMatchParticipant participant) {
        String role = new ChampionPositionNormalizer().normalize(participant.getPosition())
                .orElseThrow(() -> new IllegalArgumentException("참가자 포지션 복원 불가")).name();
        return champion(participant.getChampionId(), role);
    }

    private ChampionDto champion(int id, String role) {
        String name = championNames.computeIfAbsent(id, key -> jdbc.queryForObject("select name->>'en-US' from champions where champion_id=?", String.class, key));
        if (name == null) throw new IllegalArgumentException("Champion catalog missing: " + id);
        assertThat(champions.findChampionByName(name).getChampionId()).isEqualTo((long) id);
        return new ChampionDto(name, role);
    }

    private Map<String, Object> identity(JsonNode label) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : List.of("matchId", "participantId", "startPurchaseOrder", "endPurchaseOrder", "targetItemId", "position", "completedCoreCount")) {
            result.put(key, label.get(key));
        }
        return result;
    }

    private void exclude(Map<String, Object> row, String reason) { row.put("status", "EXCLUDED"); row.put("reason", reason); }
    private List<Long> longs(List<Integer> ids) { return ids.stream().map(Integer::longValue).toList(); }
    private ItemInventory inventory(Map<Integer, Integer> quantities) {
        var inventory = ItemInventory.empty();
        for (var entry : quantities.entrySet()) for (int i = 0; i < entry.getValue(); i++) inventory = inventory.add(entry.getKey());
        return inventory;
    }
    private Map<Integer, Integer> quantities(JsonNode node) {
        Map<Integer, Integer> values = new TreeMap<>();
        node.fields().forEachRemaining(e -> values.put(Integer.parseInt(e.getKey()), e.getValue().asInt()));
        return values;
    }
    private byte[] resourceBytes(String key) throws Exception {
        try (var input = resources.getResource(environment.getRequiredProperty(key)).getInputStream()) { return input.readAllBytes(); }
    }
    private void write(String name, Object value) throws Exception { mapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve(name).toFile(), value); }
    private static String required(String property) { return java.util.Objects.requireNonNull(System.getProperty(property), property); }
    private static String hash(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private record Scenario(String name, long gold, Map<Integer, Integer> inventory, List<Integer> enemies) {}
}
