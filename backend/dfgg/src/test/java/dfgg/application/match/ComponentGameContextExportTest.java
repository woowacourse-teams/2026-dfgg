package dfgg.application.match;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dfgg.application.recommend.component.ComponentCandidateFeatures;
import dfgg.application.recommend.component.ComponentCompositionReader;
import dfgg.application.recommend.context.CompositionFeatureCalculator;
import dfgg.application.recommend.context.CompositionFeatures;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 기존 46열의 후보/Label/분할을 보존한 비교용 export. 새 9열은 실제 학습 경로의 계산기를 사용한다. */
@Tag("evaluation")
@DataJpaTest(showSql = false, properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.show-sql=false", "spring.datasource.hikari.read-only=true"})
@ActiveProfiles("test")
@Import({ComponentCompositionReader.class, CompositionFeatureCalculator.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ComponentGameContextExportTest {
    @Autowired ComponentCompositionReader compositions;
    @Autowired JdbcTemplate jdbc;

    @Test
    void append_composition_to_preserved_baseline() throws Exception {
        var mapper = new ObjectMapper();
        Path source = Path.of(System.getProperty("evaluation.component.source"));
        Path output = Path.of(System.getProperty("evaluation.component.output"));
        if (Files.exists(output)) {
            try (var files = Files.list(output)) {
                if (files.findAny().isPresent()) throw new IllegalArgumentException("기존 Dataset을 덮어쓰지 않습니다: " + output);
            }
        }
        Files.createDirectories(output);
        var metadata = championMetadata();
        byte[] metadataBytes = mapper.writeValueAsBytes(metadata);
        Files.write(output.resolve("champion-tags.json"), metadataBytes);
        var manifest = (ObjectNode) mapper.readTree(source.resolve("manifest.json").toFile());
        var sourceHashes = mapper.createObjectNode();
        sourceHashes.put("manifest.json", sha256(source.resolve("manifest.json")));
        var report = mapper.createObjectNode();
        long totalGroups = 0, totalCandidates = 0;
        Set<String> matches = new HashSet<>();
        for (String split : List.of("train", "validation", "test")) {
            long groups = 0, candidates = 0;
            String currentMatch = null;
            Map<Integer, CompositionFeatures> contexts = Map.of();
            Path input = source.resolve(split + ".jsonl");
            sourceHashes.put(split + ".jsonl", sha256(input));
            try (var reader = Files.newBufferedReader(input); var writer = Files.newBufferedWriter(output.resolve(split + ".jsonl"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    var row = (ObjectNode) mapper.readTree(line);
                    assertThat(row.path("schemaVersion").asText()).isEqualTo("component-bundle-validation-v5");
                    assertThat(row.path("featureNames")).isEqualTo(mapper.valueToTree(ComponentCandidateFeatures.FEATURE_NAMES.subList(0, 46)));
                    String match = row.path("matchId").asText();
                    if (!match.equals(currentMatch)) {
                        contexts = compositions.forMatch(match);
                        assertThat(contexts).as("전체 경기 조합이 필요합니다: " + match).hasSize(10);
                        currentMatch = match;
                    }
                    var context = Objects.requireNonNull(contexts.get(row.path("participantId").asInt()), "경기 참가자 누락");
                    for (var candidate : row.withArray("candidates")) {
                        var vector = (ArrayNode) candidate.path("features");
                        assertThat(vector.size()).isEqualTo(46);
                        context.values().forEach(vector::add);
                        candidates++;
                    }
                    row.put("schemaVersion", "component-bundle-validation-v6");
                    row.set("featureNames", mapper.valueToTree(ComponentCandidateFeatures.FEATURE_NAMES.subList(0, 55)));
                    writer.write(mapper.writeValueAsString(row));
                    writer.newLine();
                    groups++;
                    matches.add(match);
                    if (groups % 10000 == 0) System.out.println("COMPOSITION_EXPORT " + split + " groups=" + groups);
                }
            }
            report.set(split, mapper.valueToTree(Map.of("groups", groups, "candidates", candidates)));
            totalGroups += groups;
            totalCandidates += candidates;
        }
        assertThat(championMetadata()).as("export 중 Champion metadata 변경 금지").isEqualTo(metadata);
        manifest.put("schemaVersion", "component-bundle-validation-v6");
        manifest.put("championMetadataSha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(metadataBytes)));
        manifest.set("sourceFilesSha256", sourceHashes);
        manifest.put("sourceDirectory", source.toAbsolutePath().toString());
        if (manifest.hasNonNull("preparationLookupSha256")) Files.copy(source.resolve("preparation-lookup.json"), output.resolve("preparation-lookup.json"));
        report.put("schemaVersion", "component-bundle-validation-v6");
        report.put("featureCount", 55);
        report.put("rankingGroups", totalGroups);
        report.put("candidates", totalCandidates);
        report.put("matchesWithRankingGroups", matches.size());
        report.put("sourcePolicy", "PRESERVE_ALL_BASELINE_FIELDS_EXCEPT_SCHEMA_AND_APPEND_9_FEATURES");
        mapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve("manifest.json").toFile(), manifest);
        mapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve("report.json").toFile(), report);
        System.out.println("COMPOSITION_EXPORT " + report);
    }

    private List<Map<String, Object>> championMetadata() {
        return jdbc.queryForList("select c.champion_id, t.tag from champions c left join champion_tags t on t.champion_id=c.champion_id order by c.champion_id, t.tag");
    }

    private String sha256(Path path) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        try (var in = new DigestInputStream(Files.newInputStream(path), digest)) {
            in.transferTo(java.io.OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
