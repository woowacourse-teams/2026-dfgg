package dfgg.application.match;

import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.infrastructure.external.dto.ItemResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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
@Import({ParticipantItemPurchaseSnapshotService.class, PurchaseInventoryReconstructor.class, PurchaseMaterialLinker.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ComponentDatasetExportTest {
    @Autowired ParticipantItemPurchaseSnapshotService snapshots;
    @Autowired JdbcTemplate jdbc;

    @Test
    void export() throws Exception {
        var mapper = new ObjectMapper();
        Path catalogPath = Path.of(System.getProperty("evaluation.component.catalog"));
        Path directory = Path.of(System.getProperty("evaluation.component.output"));
        Files.createDirectories(directory);
        var catalog = mapper.readValue(catalogPath.toFile(), ItemResponse.class);
        String version = catalog.dataVersion() == null ? catalog.version() : catalog.dataVersion();
        String patch = version.substring(0, version.lastIndexOf('.'));
        var service = new ComponentTrainingDataService(snapshots, mapper);
        var matches = new ArrayList<>(jdbc.queryForList(
                "select distinct match_id from normalized_match_participant_item_events where patch = ?", String.class, patch));
        matches.sort(java.util.Comparator.comparing(ComponentDatasetExportTest::hash).thenComparing(id -> id));
        Map<String, Long> statuses = new TreeMap<>();
        Map<String, Long> exclusions = new TreeMap<>();
        Map<String, Object> manifest = new LinkedHashMap<>();
        long rows = 0, eligible = 0, missing = 0, ready = 0;
        int trainEnd = matches.size() * 8 / 10;
        int validationEnd = matches.size() * 9 / 10;
        try (var all = Files.newBufferedWriter(directory.resolve("all.jsonl"));
             var train = Files.newBufferedWriter(directory.resolve("train.jsonl"));
             var validation = Files.newBufferedWriter(directory.resolve("validation.jsonl"));
             var test = Files.newBufferedWriter(directory.resolve("test.jsonl"))) {
            for (int i = 0; i < matches.size(); i++) {
                String split = i < trainEnd ? "train" : i < validationEnd ? "validation" : "test";
                var writer = i < trainEnd ? train : i < validationEnd ? validation : test;
                long splitReady = 0;
                for (var example : service.examplesForMatch(matches.get(i), catalog)) {
                    rows++;
                    statuses.merge(example.status(), 1L, Long::sum);
                    for (String reason : example.exclusionReasons()) exclusions.merge(reason, 1L, Long::sum);
                    boolean comparable = List.of("READY", "OBSERVED_BUNDLE_MISSING", "OBSERVED_COST_MISMATCH",
                            "OBSERVED_COST_NOT_CONFIRMED", "OBSERVED_COST_OUTSIDE_GOLD_RANGE").contains(example.status());
                    if (comparable) {
                        eligible++;
                        if (example.candidates().stream().noneMatch(c -> c.itemQuantities().equals(example.observedItems()))) missing++;
                    }
                    String json = mapper.writeValueAsString(example) + "\n";
                    all.write(json);
                    if (example.status().equals("READY") && example.candidates().size() > 1) {
                        writer.write(json);
                        ready++;
                        splitReady++;
                    }
                }
                manifest.put(matches.get(i), Map.of("split", split, "rankingGroups", splitReady));
                if ((i + 1) % 100 == 0) System.out.println("COMPONENT_EXPORT matches=" + (i + 1) + "/" + matches.size());
            }
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("manifest.json").toFile(),
                Map.of("targetSource", "OBSERVED_FUTURE_CORE", "splitPolicy", "sha256(component-v1:matchId), sorted 80/10/10",
                        "catalogSha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(catalogPath))),
                        "matches", manifest));
        var report = new LinkedHashMap<String, Object>();
        report.put("matches", matches.size()); report.put("bundles", rows);
        report.put("candidateComparableBundles", eligible); report.put("missingObservedBundles", missing);
        report.put("missingRate", eligible == 0 ? null : (double) missing / eligible);
        report.put("rankingGroups", ready); report.put("statuses", statuses); report.put("exclusionReasonsOverlapping", exclusions);
        mapper.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("report.json").toFile(), report);
        System.out.println("COMPONENT_EXPORT " + mapper.writeValueAsString(report));
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
