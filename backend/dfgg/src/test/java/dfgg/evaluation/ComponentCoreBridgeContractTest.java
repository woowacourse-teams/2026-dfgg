package dfgg.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.application.recommend.component.ComponentCandidateFeatures;
import dfgg.application.recommend.v3.ranker.LightGbmModelLoader;
import dfgg.domain.match.BuildPathResolver;
import dfgg.domain.match.ItemInventory;
import dfgg.infrastructure.config.ComponentLtrConfiguration;
import dfgg.infrastructure.external.catalog.DataDragonPurchaseItemCatalog;
import dfgg.infrastructure.external.dto.ItemResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** 조사용 반례: 운영 계산기를 사용하지만 실제 경기나 Core Top-5 성능 표본은 아니다. */
@Tag("evaluation")
class ComponentCoreBridgeContractTest {
    @Test
    void probe_target_core_distinguishability() throws Exception {
        var mapper = new ObjectMapper();
        Path catalogPath = Path.of(System.getProperty("evaluation.bridge.catalog"));
        Path componentPath = Path.of(System.getProperty("evaluation.bridge.component-model"));
        Path corePath = Path.of(System.getProperty("evaluation.bridge.core-model"));
        var response = mapper.readValue(catalogPath.toFile(), ItemResponse.class);
        var catalog = new DataDragonPurchaseItemCatalog(response.data());
        var resolver = new BuildPathResolver(catalog);
        var before = ItemInventory.empty();
        var branches = new ArrayList<Map<String, Object>>();
        var vectors = new ArrayList<List<Double>>();
        try (var input = Files.newInputStream(componentPath)) {
            var model = LightGbmModelLoader.load(input, ComponentCandidateFeatures.FEATURE_NAMES,
                    ComponentLtrConfiguration.SCHEMA_FINGERPRINT);
            for (int target : List.of(3071, 6333)) {
                var candidates = resolver.resolve(target, before, 350);
                var sword = candidates.stream().filter(c -> c.itemQuantities().equals(Map.of(1036, 1)))
                        .findFirst().orElseThrow();
                var features = ComponentCandidateFeatures.calculate(before, sword, target, catalog,
                        response, 600_000, "TOP");
                vectors.add(features.features());
                double score = model.predict(features.features().stream().mapToDouble(Double::doubleValue).toArray());
                assertThat(score).isFinite();
                branches.add(Map.of("targetCoreId", target, "targetName", response.data().get("" + target).name(),
                        "candidateCount", candidates.size(), "candidateItems", sword.itemQuantities(),
                        "features", features.features(), "componentScore", score));
            }
        }
        assertThat(vectors.get(0)).hasSize(46);
        assertThat(vectors.get(0).subList(0, 41)).isEqualTo(vectors.get(1).subList(0, 41));
        assertThat(vectors.get(0).subList(41, 46)).isNotEqualTo(vectors.get(1).subList(41, 46));

        var coreJson = mapper.readTree(corePath.toFile());
        assertThat(coreJson.path("objective").asText()).isEqualTo("lambdarank");
        try (var input = Files.newInputStream(corePath)) {
            // 운영 Core의 feature 이름·순서·fingerprint 계약까지 확인한다.
            LightGbmModelLoader.load(input);
        }
        var report = new LinkedHashMap<String, Object>();
        report.put("purpose", "SYNTHETIC_CONTRACT_PROBE_NOT_SERVICE_EVALUATION");
        report.put("inventoryBefore", Map.of());
        report.put("budget", 350);
        report.put("gameTimeMs", 600_000);
        report.put("position", "TOP");
        report.put("dataVersion", response.version());
        report.put("featureNames", ComponentCandidateFeatures.FEATURE_NAMES);
        report.put("identicalFeatureVectors", vectors.get(0).equals(vectors.get(1)));
        report.put("branches", branches);
        report.put("coreModelContractPassed", true);
        report.put("coreModelSha256", sha256(corePath));
        report.put("componentModelSha256", sha256(componentPath));
        report.put("catalogSha256", sha256(catalogPath));
        Path output = Path.of(System.getProperty("evaluation.bridge.output"));
        Files.createDirectories(output);
        mapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve("target_core_probe.json").toFile(), report);
    }

    private static String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
