package dfgg.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.application.item.ItemPurchaseTypeClassifier;
import dfgg.application.match.ParticipantItemPurchaseBundle;
import dfgg.application.match.ParticipantItemPurchaseSnapshotService;
import dfgg.application.match.PurchaseInventoryReconstructor;
import dfgg.application.recommend.component.ComponentCandidateFeatures;
import dfgg.application.recommend.v3.*;
import dfgg.application.recommend.v3.ranker.CandidateRanker;
import dfgg.application.recommend.v3.ranker.GradientBoostedTrees;
import dfgg.domain.item.Item;
import dfgg.domain.item.ItemRepository;
import dfgg.domain.match.BuildPathResolver;
import dfgg.domain.match.ItemPurchaseType;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import dfgg.infrastructure.external.catalog.DataDragonPurchaseItemCatalog;
import dfgg.infrastructure.external.dto.ItemResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** 운영 통계 snapshot 조건의 기술적 리플레이. 독립 holdout 성능으로 표시하지 않는다. */
@Tag("evaluation")
@ActiveProfiles("evaluation")
@SpringBootTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=none",
        "spring.datasource.hikari.read-only=true", "collection.scheduler.enabled=false"})
@Transactional(readOnly = true)
class ComponentCoreBridgeReplayTest {
    @Autowired ParticipantItemPurchaseSnapshotService snapshots;
    @Autowired PurchaseInventoryReconstructor inventoryReconstructor;
    @Autowired NormalizedMatchParticipantRepository participants;
    @Autowired ItemRepository items;
    @Autowired dfgg.application.recommend.context.CompositionFeatureCalculator compositions;
    @Autowired List<CandidateGenerator> generators;
    @Autowired CandidateTopK retrievalLimits;
    @Autowired HardValidityFilter validity;
    @Autowired CandidateRanker coreRanker;
    @Autowired @Qualifier("componentLtrModel") GradientBoostedTrees componentModel;
    @Autowired JdbcTemplate jdbc;

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, Integer> exclusions = new TreeMap<>();
    private final List<Map<String, Object>> excludedRows = new ArrayList<>();
    private ItemResponse response;
    private DataDragonPurchaseItemCatalog catalog;
    private BuildPathResolver resolver;
    private Map<Integer, ItemPurchaseType> purchaseTypes;

    @Test
    void replay_each_predicted_core_independently() throws Exception {
        Path output = Path.of(required("evaluation.bridge.output"));
        Files.createDirectories(output);
        response = mapper.readValue(Path.of(required("evaluation.bridge.catalog")).toFile(), ItemResponse.class);
        assertThat(response.version()).isEqualTo("16.18.1");
        catalog = new DataDragonPurchaseItemCatalog(response.data());
        resolver = new BuildPathResolver(catalog);
        purchaseTypes = new ItemPurchaseTypeClassifier().classify(response.data());
        Map<String, List<JsonNode>> byMatch = new TreeMap<>();
        try (var lines = Files.lines(Path.of(required("evaluation.bridge.test-data")))) {
            for (String line : lines.toList()) {
                JsonNode point = mapper.readTree(line);
                assertThat(point.path("status").asText()).isEqualTo("READY");
                assertThat(point.path("candidates").size()).isGreaterThanOrEqualTo(2);
                assertThat(point.path("dataVersion").asText()).isEqualTo(response.version());
                byMatch.computeIfAbsent(point.path("matchId").asText(), ignored -> new ArrayList<>()).add(point);
            }
        }
        Set<String> available = new HashSet<>(jdbc.queryForList(
                "select match_id from normalized_match_participants group by match_id having count(*)=10", String.class));
        Set<String> inStats = new HashSet<>(jdbc.queryForList(
                "select distinct match_id from composition_stats_samples", String.class));
        var states = new ArrayList<ReplayPoint>();
        int matchesPrepared = 0;
        for (var entry : byMatch.entrySet()) {
            if (!available.contains(entry.getKey())) {
                entry.getValue().forEach(p -> exclude(p, "MATCH_PARTICIPANTS_MISSING"));
                continue;
            }
            prepare(entry.getKey(), entry.getValue(), states, inStats.contains(entry.getKey()));
            if (++matchesPrepared % 25 == 0) System.out.println("BRIDGE_PREPARE matches=" + matchesPrepared);
        }
        states.sort(Comparator.comparing((ReplayPoint p) -> hash("MD5", identity(p.label())))
                .thenComparing(p -> identity(p.label())));
        int limit = Integer.getInteger("evaluation.bridge.limit", 300);
        var selected = limit <= 0 ? states : states.subList(0, Math.min(limit, states.size()));
        assertThat(selected).as("추론 가능한 첫 Core 상태가 필요합니다").isNotEmpty();
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("purpose", "PRODUCTION_SNAPSHOT_DIAGNOSTIC_NOT_INDEPENDENT_HOLDOUT");
        metadata.put("dataVersion", response.version());
        metadata.put("componentTestGroups", byMatch.values().stream().mapToInt(List::size).sum());
        metadata.put("eligibleFirstCoreGroups", states.size());
        metadata.put("selectedGroups", selected.size());
        metadata.put("sampling", "MD5(matchId:participantId:startPurchaseOrder), identity tie-break");
        metadata.put("limit", limit);
        metadata.put("exclusions", exclusions);
        metadata.put("firstCorePolicy", "NO_CURRENTLY_OWNED_PAST_CORE_OR_BOOTS_AS_IN_EXISTING_CONNECTION_CODE");
        metadata.put("goldPolicy", "EXISTING_RECONSTRUCTED_GOLD_RANGE_LOWER_NOT_PREFIX_ONLY");
        metadata.put("coreModelSha256", fileHash(required("evaluation.bridge.core-model")));
        metadata.put("componentModelSha256", fileHash(required("evaluation.bridge.component-model")));
        metadata.put("catalogSha256", fileHash(required("evaluation.bridge.catalog")));
        metadata.put("testDataSha256", fileHash(required("evaluation.bridge.test-data")));
        metadata.put("featureNames", ComponentCandidateFeatures.FEATURE_NAMES);
        metadata.put("purchaseTypes", purchaseTypes);
        mapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve("replay_metadata.json").toFile(), metadata);
        try (var writer = Files.newBufferedWriter(output.resolve("excluded_groups.jsonl"))) {
            for (var row : excludedRows) writer.write(mapper.writeValueAsString(row) + "\n");
        }
        // 예측 branch는 정답 필드를 받지 않는다. 평가 라벨은 추론 후에 참조한다.
        try (var writer = Files.newBufferedWriter(output.resolve("raw_results.jsonl"))) {
            int done = 0;
            for (ReplayPoint point : selected) {
                Map<String, Object> row = replay(point);
                writer.write(mapper.writeValueAsString(row) + "\n");
                writer.flush();
                if (++done % 25 == 0) System.out.println("BRIDGE_REPLAY groups=" + done + "/" + selected.size());
            }
        }
        System.out.println("BRIDGE_DONE groups=" + selected.size() + " eligible=" + states.size());
    }

    private void prepare(String matchId, List<JsonNode> labels, List<ReplayPoint> states, boolean inStats) {
        var matchParticipants = participants.findByMatchId(matchId);
        var bundles = snapshots.bundlesForMatch(matchId);
        var purchases = snapshots.snapshotsForMatch(matchId);
        for (JsonNode label : labels) {
            int participant = label.path("participantId").asInt();
            int order = label.path("startPurchaseOrder").asInt();
            var bundle = bundles.stream().filter(b -> b.participantId() == participant && b.startPurchaseOrder() == order)
                    .findFirst().orElse(null);
            if (bundle == null || !bundle.ready()) { exclude(label, "REPLAY_BUNDLE_NOT_READY"); continue; }
            if (!mapper.valueToTree(bundle.inventoryBefore().quantities()).equals(label.path("inventoryBefore"))
                    || bundle.goldLower() != label.path("goldLower").asInt()
                    || bundle.goldUpper() != label.path("goldUpper").asInt()) {
                exclude(label, "SNAPSHOT_DATASET_MISMATCH"); continue;
            }
            var pastCores = purchases.stream().filter(p -> p.purchase().getParticipantId() == participant)
                    .filter(p -> p.purchase().getPurchaseOrder() < order)
                    .filter(p -> p.purchase().getPurchaseType() == ItemPurchaseType.CORE
                            || p.purchase().getPurchaseType() == ItemPurchaseType.BOOTS)
                    .filter(p -> inventoryReconstructor.isStillOwned(p.purchase().getItemId(), bundle.inventoryBefore(), "16.18"))
                    .map(p -> Long.valueOf(p.purchase().getItemId())).distinct().toList();
            if (!pastCores.isEmpty()) { exclude(label, "NOT_FIRST_CORE_STATE"); continue; }
            try {
                var query = new SnapshotQueryBuilder().buildAtPurchase(matchParticipants, participant, "16.18", pastCores);
                states.add(new ReplayPoint(label, bundle, query, inStats));
            } catch (IllegalArgumentException exception) {
                exclude(label, "QUERY_BUILD_FAILED: " + exception.getMessage());
            }
        }
    }

    private Map<String, Object> replay(ReplayPoint point) {
        var row = new LinkedHashMap<String, Object>();
        JsonNode label = point.label();
        var bundle = point.bundle();
        row.put("matchId", label.path("matchId").asText());
        row.put("participantId", label.path("participantId").asInt());
        row.put("purchaseIndex", label.path("startPurchaseOrder").asInt());
        row.put("timestamp", bundle.purchases().getFirst().sourceEvent().gameTimeMs());
        row.put("championId", point.query().myChampionId());
        row.put("role", point.query().position().name());
        row.put("inventoryBefore", bundle.inventoryBefore().quantities());
        row.put("inventorySlotCount", catalog.occupiedSlots(bundle.inventoryBefore()));
        row.put("bootsInQuestSlot", bundle.inventoryBefore().bootsInQuestSlot());
        row.put("controlWardsInQuestSlot", bundle.inventoryBefore().controlWardsInQuestSlot());
        row.put("goldRangeLower", bundle.goldLower());
        row.put("goldRangeUpper", bundle.goldUpper());
        row.put("statisticsContainsMatch", point.inStats());
        List<Map<String, Object>> branches;
        try {
            branches = inferBranches(bundle, point.query());
            row.put("corePredictions", branches);
        } catch (RuntimeException exception) {
            row.put("corePredictions", List.of());
            row.put("firstFailureReason", "CORE_FEATURE_OR_MODEL_INFERENCE_FAILED");
            row.put("error", exception.getClass().getSimpleName() + ": " + exception.getMessage());
            return row;
        }
        // 미래 정답은 위의 서비스형 추론에 전달하지 않고, 여기서 평가와 Oracle 대조에만 사용한다.
        int observedCore = label.path("targetItemId").asInt();
        Map<Integer, Integer> observedItems = quantities(label.path("observedItems"));
        row.put("observedFutureCoreId", observedCore);
        row.put("observedItems", observedItems);
        var oracle = inferComponent(bundle, point.query(), observedCore);
        row.put("oracle", oracle);
        row.put("oracleObservedRank", observedRank(oracle, observedItems));
        row.put("oracleMatchesExportedCandidates", matchesExport(oracle, label.path("candidates")));
        var observedBranch = branches.stream().filter(b -> (int) b.get("coreId") == observedCore).findFirst().orElse(null);
        row.put("observedCoreRank", observedBranch == null ? null : observedBranch.get("rank"));
        row.put("observedCoreCandidateCount", observedBranch == null ? null : observedBranch.get("candidateCount"));
        Integer rank = observedBranch == null ? null : observedRank(observedBranch, observedItems);
        row.put("observedPurchaseCandidatePresent", rank != null);
        row.put("observedPurchaseComponentRank", rank);
        String failure = observedBranch == null ? "OBSERVED_CORE_NOT_IN_TOP5"
                : observedBranch.get("failureReason") != null ? (String) observedBranch.get("failureReason")
                : (int) observedBranch.get("candidateCount") == 0 ? "NO_COMPONENT_CANDIDATE"
                : rank == null ? "OBSERVED_PURCHASE_CANDIDATE_MISSING"
                : rank != 1 ? "COMPONENT_RANK_MISS" : null;
        row.put("firstFailureReason", failure);
        return row;
    }

    /** 정답 라벨을 받지 않는 입력 경계. 각 Core의 후보를 섞지 않고 순위를 매긴다. */
    private List<Map<String, Object>> inferBranches(ParticipantItemPurchaseBundle bundle, RecommendationQuery query) {
        var union = CandidateUnion.merge(generators.stream().map(g -> g.generate(query, retrievalLimits.of(g.source()))).toList());
        var itemById = items.findAllById(union.candidates().stream().map(ItemCandidate::itemId).toList()).stream()
                .collect(Collectors.toMap(Item::getItemId, Function.identity()));
        var cores = coreRanker.rank(validity.filter(union, query.purchasedItemIds(), itemById), query, 5);
        var branches = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < cores.size(); i++) {
            var core = cores.get(i);
            int id = Math.toIntExact(core.itemId());
            if (!Double.isFinite(core.modelScore())) throw new IllegalStateException("Nonfinite Core score");
            var branch = inferComponent(bundle, query, id);
            branch.put("rank", i + 1);
            branch.put("score", core.modelScore());
            // Core feature의 결측 NaN은 JSON null로 기록한다.
            branch.put("coreFeatures", Arrays.stream(core.features().values()).boxed().map(v -> Double.isFinite(v) ? v : null).toList());
            branches.add(branch);
        }
        return branches;
    }

    private Map<String, Object> inferComponent(ParticipantItemPurchaseBundle bundle, RecommendationQuery query, int target) {
        var branch = new LinkedHashMap<String, Object>();
        branch.put("coreId", target);
        branch.put("name", response.data().containsKey("" + target) ? response.data().get("" + target).name() : null);
        branch.put("purchaseType", purchaseTypes.getOrDefault(target, ItemPurchaseType.OTHER).name());
        branch.put("candidateCount", 0);
        branch.put("componentPredictions", List.of());
        var result = new dfgg.application.recommend.component.ComponentRecommendationService(componentModel)
                .recommend(List.of(target), bundle.inventoryBefore(), bundle.goldLower(),
                        bundle.purchases().getFirst().sourceEvent().gameTimeMs(), query.position().name(), query.patch(), response, compositions.calculate(query.allyChampionIds(), query.enemyChampionIds())).getFirst();
        var predictions = new ArrayList<Map<String, Object>>();
        for (var candidate : result.rankedPurchases()) {
            var prediction = new LinkedHashMap<String, Object>();
            prediction.put("candidateItems", candidate.candidate().itemQuantities());
            prediction.put("candidateFeatures", candidate.candidate().features());
            prediction.put("componentScore", candidate.score());
            prediction.put("componentRank", candidate.rankWithinTarget());
            predictions.add(prediction);
        }
        branch.put("candidateCount", predictions.size());
        branch.put("componentPredictions", predictions);
        branch.put("candidateGenerationStatus", result.status() == dfgg.application.recommend.component.ComponentRecommendationService.Status.OK
                ? (predictions.size() == 1 ? "ONLY_ONE_COMPONENT_CANDIDATE" : "READY") : result.status().name());
        branch.put("failureReason", result.reason());
        return branch;
    }

    private Integer observedRank(Map<String, Object> branch, Map<Integer, Integer> observed) {
        for (Object raw : (List<?>) branch.get("componentPredictions")) {
            var prediction = (Map<?, ?>) raw;
            if (prediction.get("candidateItems").equals(observed)) return (Integer) prediction.get("componentRank");
        }
        return null;
    }

    private boolean matchesExport(Map<String, Object> oracle, JsonNode exported) {
        JsonNode predictions = mapper.valueToTree(oracle.get("componentPredictions"));
        if (predictions.size() != exported.size()) return false;
        for (JsonNode candidate : exported) {
            boolean present = false;
            for (JsonNode prediction : predictions) {
                if (candidate.path("itemQuantities").equals(prediction.path("candidateItems"))
                        && candidate.path("features").equals(prediction.path("candidateFeatures"))) present = true;
            }
            if (!present) return false;
        }
        return true;
    }

    private Map<Integer, Integer> quantities(JsonNode node) {
        Map<Integer, Integer> result = new TreeMap<>();
        node.fields().forEachRemaining(e -> result.put(Integer.parseInt(e.getKey()), e.getValue().asInt()));
        return result;
    }

    private void exclude(JsonNode point, String reason) {
        exclusions.merge(reason, 1, Integer::sum);
        excludedRows.add(Map.of("identity", identity(point), "reason", reason));
    }

    private static String identity(JsonNode point) {
        return point.path("matchId").asText() + ":" + point.path("participantId").asInt() + ":" + point.path("startPurchaseOrder").asInt();
    }

    private static String required(String property) {
        return Objects.requireNonNull(System.getProperty(property), property);
    }

    private static String hash(String algorithm, String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static String fileHash(String file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of(file))));
    }

    private record ReplayPoint(JsonNode label, ParticipantItemPurchaseBundle bundle, RecommendationQuery query, boolean inStats) {}
}
