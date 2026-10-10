package dfgg.application.match;

import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.infrastructure.external.dto.ItemResponse;
import java.nio.charset.StandardCharsets;
import java.io.Writer;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 명시적으로 evaluationTest에서만 실행하는 실제 DB 읽기 전용 export. */
@Tag("evaluation")
@DataJpaTest(showSql = false, properties = {"spring.flyway.enabled=false", "spring.jpa.show-sql=false"})
@ActiveProfiles("test")
@Import({ParticipantItemPurchaseSnapshotService.class, PurchaseInventoryReconstructor.class, PurchaseMaterialLinker.class, dfgg.application.recommend.component.ComponentCompositionReader.class, dfgg.application.recommend.context.CompositionFeatureCalculator.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ComponentDatasetExportTest {
    @Autowired ParticipantItemPurchaseSnapshotService snapshots;
    @Autowired JdbcTemplate jdbc;
    @Autowired dfgg.application.recommend.component.ComponentCompositionReader compositions;

    @Test
    void export() throws Exception {
        var mapper = new ObjectMapper();
        Path catalogPath = Path.of(System.getProperty("evaluation.component.catalog"));
        Path directory = Path.of(System.getProperty("evaluation.component.output"));
        if (Files.exists(directory)) {
            try (var existing = Files.list(directory)) {
                if (existing.findAny().isPresent()) throw new IllegalArgumentException("기존 Dataset을 덮어쓰지 않습니다: " + directory);
            }
        }
        Files.createDirectories(directory);
        var championMetadata = jdbc.queryForList("select c.champion_id, t.tag from champions c left join champion_tags t on t.champion_id=c.champion_id order by c.champion_id, t.tag");
        byte[] championMetadataBytes = mapper.writeValueAsBytes(championMetadata);
        Files.write(directory.resolve("champion-tags.json"), championMetadataBytes);
        var catalog = mapper.readValue(catalogPath.toFile(), ItemResponse.class);
        String version = catalog.dataVersion() == null ? catalog.version() : catalog.dataVersion();
        String patch = version.substring(0, version.lastIndexOf('.'));
        String participantSource = System.getProperty("evaluation.component.participants");
        Map<String, Map<Integer, Integer>> participantMetadata = participantMetadata(mapper, participantSource, patch);
        String lookupSource = System.getProperty("evaluation.component.preparation-lookup");
        var lookup = lookupSource == null ? dfgg.domain.match.PreparationLookup.disabled()
                : dfgg.infrastructure.recommend.PreparationLookupLoader.load(Files.readAllBytes(Path.of(lookupSource)));
        var service = new ComponentTrainingDataService(snapshots, mapper, lookup, compositions);
        boolean scopeOnly = Boolean.getBoolean("evaluation.component.scope-only");
        String comparisonSource = System.getProperty("evaluation.component.compare-source");
        var matches = new ArrayList<>(jdbc.queryForList(
                "select distinct match_id from normalized_match_participant_item_events where patch = ?", String.class, patch));
        matches.sort(java.util.Comparator.comparing(ComponentDatasetExportTest::hash).thenComparing(id -> id));
        Map<String, Long> statuses = new TreeMap<>();
        Map<String, Long> exclusions = new TreeMap<>();
        Map<String, Long> targetKinds = new TreeMap<>();
        Map<String, Long> scopeStatuses = new TreeMap<>();
        Map<String, Long> outsideTrainingStatuses = new TreeMap<>();
        long outsideRows = 0, missingChampion = 0, comparedRows = 0;
        var policy = new dfgg.domain.match.ComponentTargetPolicy(new dfgg.infrastructure.external.catalog.DataDragonPurchaseItemCatalog(catalog.data()));
        Map<String, Object> manifest = new LinkedHashMap<>();
        long rows = 0, eligible = 0, missing = 0, ready = 0;
        int trainEnd = matches.size() * 8 / 10;
        int validationEnd = matches.size() * 9 / 10;
        if (lookup.enabled()) {
            var lookupJson = mapper.readTree(Path.of(lookupSource).toFile());
            var expectedTrain = new java.util.TreeSet<String>();
            lookupJson.get("trainMatchIds").forEach(id -> expectedTrain.add(id.asText()));
            if (!expectedTrain.equals(new java.util.TreeSet<>(matches.subList(0, trainEnd)))) {
                throw new IllegalArgumentException("Lookup mining Train 경기와 export split이 다릅니다.");
            }
            Files.copy(Path.of(lookupSource), directory.resolve("preparation-lookup.json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        try (var all = trainingWriter(directory.resolve(Boolean.getBoolean("evaluation.component.gzip-all") ? "all.jsonl.gz" : "all.jsonl"), scopeOnly);
             var train = trainingWriter(directory.resolve("train.jsonl"), scopeOnly);
             var validation = trainingWriter(directory.resolve("validation.jsonl"), scopeOnly);
             var test = trainingWriter(directory.resolve("test.jsonl"), scopeOnly);
             var outside = new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(
                     directory.resolve("out-of-target-path-purchases.jsonl.gz"))), StandardCharsets.UTF_8);
             var unresolved = new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(
                     directory.resolve("candidate-scope-unresolved.jsonl.gz"))), StandardCharsets.UTF_8);
             var baseline = comparisonSource == null ? null : Files.newBufferedReader(Path.of(comparisonSource))) {
            for (int i = 0; i < matches.size(); i++) {
                String split = i < trainEnd ? "train" : i < validationEnd ? "validation" : "test";
                var writer = i < trainEnd ? train : i < validationEnd ? validation : test;
                long splitReady = 0;
                Map<Integer, Integer> championIds = new TreeMap<>(participantMetadata.getOrDefault(matches.get(i), Map.of()));
                jdbc.query("select participant_id, champion_id from normalized_match_participants where match_id = ?",
                        (org.springframework.jdbc.core.RowCallbackHandler) row -> {
                            Integer previous = championIds.put(row.getInt(1), row.getInt(2));
                            if (previous != null && previous != row.getInt(2)) throw new IllegalStateException("Champion metadata 불일치");
                        }, matches.get(i));
                var dataset = service.datasetForMatch(matches.get(i), catalog, championIds);
                for (var diagnostic : dataset.diagnostics()) {
                    scopeStatuses.merge(diagnostic.status(), 1L, Long::sum);
                    if (diagnostic.status().equals("OUT_OF_TARGET_PATH_PURCHASE")) {
                        outsideRows++;
                        if (diagnostic.championId() == null) missingChampion++;
                        outsideTrainingStatuses.merge(diagnostic.trainingStatus(), 1L, Long::sum);
                        outside.write(mapper.writeValueAsString(diagnostic) + "\n");
                    } else if (diagnostic.status().startsWith("UNRESOLVED")
                            || diagnostic.status().equals("TARGET_CANCELLATION_UNRESOLVED")) {
                        unresolved.write(mapper.writeValueAsString(diagnostic) + "\n");
                    }
                }
                for (var example : dataset.examples()) {
                    rows++;
                    statuses.merge(example.status(), 1L, Long::sum);
                    for (String reason : example.exclusionReasons()) exclusions.merge(reason, 1L, Long::sum);
                    boolean comparable = List.of("READY", "OBSERVED_BUNDLE_MISSING", "OBSERVED_COST_MISMATCH",
                            "OBSERVED_COST_NOT_CONFIRMED", "OBSERVED_COST_OUTSIDE_GOLD_RANGE").contains(example.status());
                    if (comparable) {
                        eligible++;
                        if (example.candidates().stream().noneMatch(c -> c.candidate().itemQuantities().equals(example.observedItems()))) missing++;
                    }
                    String json = mapper.writeValueAsString(example) + "\n";
                    if (baseline != null) {
                        String original = baseline.readLine();
                        if (original == null || !mapper.readTree(original).equals(mapper.readTree(json))) {
                            throw new AssertionError("학습 행 변경: " + example.matchId() + ":"
                                    + example.participantId() + ":" + example.startPurchaseOrder());
                        }
                        comparedRows++;
                    }
                    all.write(json);
                    if (example.status().equals("READY") && example.candidates().size() > 1) {
                        writer.write(json);
                        targetKinds.merge(policy.kind(example.targetItemId()).name(), 1L, Long::sum);
                        ready++;
                        splitReady++;
                    }
                }
                manifest.put(matches.get(i), Map.of("split", split, "rankingGroups", splitReady));
                if ((i + 1) % 100 == 0) System.out.println("COMPONENT_EXPORT matches=" + (i + 1) + "/" + matches.size());
            }
            if (baseline != null && baseline.readLine() != null) throw new AssertionError("기존 학습 행이 더 많습니다.");
        }
        var datasetManifest = new LinkedHashMap<String, Object>(Map.of("targetSource", "OBSERVED_FUTURE_TARGET_ITEM", "splitPolicy", "sha256(component-v1:matchId), sorted 80/10/10",
                        "catalogSha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(catalogPath))),
                        "matches", manifest));
        if (!championMetadata.equals(jdbc.queryForList("select c.champion_id, t.tag from champions c left join champion_tags t on t.champion_id=c.champion_id order by c.champion_id, t.tag"))) {
            throw new IllegalStateException("export 중 Champion metadata가 변경되었습니다.");
        }
        datasetManifest.put("schemaVersion", ComponentTrainingDataService.SCHEMA_VERSION);
        datasetManifest.put("championMetadataSha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(championMetadataBytes)));
        if (lookup.enabled()) datasetManifest.put("preparationLookupSha256", lookup.sha256());
        mapper.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("manifest.json").toFile(), datasetManifest);
        var report = new LinkedHashMap<String, Object>();
        report.put("schemaVersion", ComponentTrainingDataService.SCHEMA_VERSION);
        report.put("rankingGroupsByTargetKind", targetKinds);
        report.put("matches", matches.size()); report.put("bundles", rows);
        report.put("candidateComparableBundles", eligible); report.put("missingObservedBundles", missing);
        report.put("missingRate", eligible == 0 ? null : (double) missing / eligible);
        report.put("rankingGroups", ready); report.put("statuses", statuses); report.put("exclusionReasonsOverlapping", exclusions);
        mapper.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("report.json").toFile(), report);
        Map<String, Object> scopeReport = new LinkedHashMap<>();
        scopeReport.put("schemaVersion", "component-candidate-scope-v1");
        scopeReport.put("purpose", "DIAGNOSTIC_ONLY_NOT_TRAINING_CANDIDATES");
        scopeReport.put("matches", matches.size()); scopeReport.put("bundles", rows);
        scopeReport.put("statuses", scopeStatuses);
        scopeReport.put("outsideTargetPathPurchases", outsideRows);
        scopeReport.put("outsideTrainingStatuses", outsideTrainingStatuses);
        scopeReport.put("outsideMissingChampionCount", missingChampion);
        scopeReport.put("scopeOnly", scopeOnly);
        scopeReport.put("comparedTrainingRows", comparedRows);
        scopeReport.put("comparisonSource", comparisonSource);
        scopeReport.put("participantMetadataSource", participantSource);
        scopeReport.put("participantMetadataSha256", participantSource == null ? null : HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of(participantSource)))));
        scopeReport.put("futureLinkEvidence", "RECIPE_MEMBERSHIP_ONLY_NOT_MATERIAL_CONSUMPTION");
        mapper.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("candidate-scope-summary.json").toFile(), scopeReport);
        System.out.println("COMPONENT_SCOPE " + mapper.writeValueAsString(scopeReport));
        System.out.println("COMPONENT_EXPORT " + mapper.writeValueAsString(report));
    }

    private Writer trainingWriter(Path path, boolean scopeOnly) throws java.io.IOException {
        if (scopeOnly) return Writer.nullWriter();
        if (path.toString().endsWith(".gz")) return new java.io.BufferedWriter(new OutputStreamWriter(
                new GZIPOutputStream(Files.newOutputStream(path)), StandardCharsets.UTF_8));
        return Files.newBufferedWriter(path);
    }

    /** 별도 읽기 전용 DB에서 추출한 참가자 메타데이터도 명시적으로 연결할 수 있다. */
    private Map<String, Map<Integer, Integer>> participantMetadata(ObjectMapper mapper, String source, String patch)
            throws java.io.IOException {
        Map<String, Map<Integer, Integer>> participants = new TreeMap<>();
        if (source == null) return participants;
        try (var reader = Files.newBufferedReader(Path.of(source))) {
            String line;
            while ((line = reader.readLine()) != null) {
                var row = mapper.readTree(line);
                if (!patch.equals(row.path("patch").asText()) || row.path("matchId").asText().isBlank()
                        || !row.path("participantId").isInt() || row.path("participantId").asInt() <= 0
                        || !row.path("championId").isInt() || row.path("championId").asInt() <= 0) {
                    throw new IllegalArgumentException("참가자 메타데이터의 패치·경기·참가자·Champion ID가 필요합니다.");
                }
                var match = participants.computeIfAbsent(row.get("matchId").asText(), ignored -> new TreeMap<>());
                if (match.putIfAbsent(row.get("participantId").asInt(), row.get("championId").asInt()) != null) {
                    throw new IllegalArgumentException("중복 참가자 메타데이터");
                }
            }
        }
        return participants;
    }

    /** 누락 사례의 현재 복원 상태·후보·전체 이벤트를 기존 계산기로 재현한다. */
    @Test
    void inspect_missing_examples() throws Exception {
        if (System.getProperty("evaluation.component.inspect") == null) return;
        var mapper = new ObjectMapper();
        var response = mapper.readValue(Path.of(System.getProperty("evaluation.component.catalog")).toFile(), ItemResponse.class);
        var catalog = new dfgg.infrastructure.external.catalog.DataDragonPurchaseItemCatalog(response.data());
        var service = new ComponentTrainingDataService(snapshots, mapper, dfgg.domain.match.PreparationLookup.disabled(), compositions);
        var reports = new ArrayList<Map<String, Object>>();
        for (String matchId : System.getProperty("evaluation.component.inspect").split(",")) {
            var examples = service.examplesForMatch(matchId, response);
            var states = snapshots.snapshotsForMatch(matchId);
            var report = new LinkedHashMap<String, Object>();
            report.put("matchId", matchId);
            report.put("examples", examples);
            report.put("missingExamples", examples.stream().filter(e -> e.status().equals("OBSERVED_BUNDLE_MISSING")).toList());
            report.put("purchaseStates", states.stream().map(state -> {
                var row = new LinkedHashMap<String, Object>();
                row.put("participantId", state.purchase().getParticipantId());
                row.put("purchaseOrder", state.purchase().getPurchaseOrder());
                row.put("source", state.sourceEvent());
                row.put("reason", state.reason());
                row.put("inventory", state.inventoryBefore() == null ? null : state.inventoryBefore().quantities());
                row.put("bootsInQuestSlot", state.inventoryBefore() != null && state.inventoryBefore().bootsInQuestSlot());
                row.put("slots", state.inventoryBefore() == null ? null : catalog.occupiedSlots(state.inventoryBefore()));
                return row;
            }).toList());
            report.put("events", jdbc.queryForList("select participant_id,event_order,purchase_order,event_type,item_id,game_time_ms,undo_before_item_id,undo_after_item_id from normalized_match_participant_item_events where match_id=? order by participant_id,event_order", matchId));
            reports.add(report);
        }
        Path output = Path.of(System.getProperty("evaluation.component.output"));
        Files.createDirectories(output);
        mapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve("missing-replay.json").toFile(), reports);
        System.out.println("COMPONENT_MISSING_REPLAY matches=" + reports.size());
    }

    private static String hash(String matchId) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(("component-v1:" + matchId).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
