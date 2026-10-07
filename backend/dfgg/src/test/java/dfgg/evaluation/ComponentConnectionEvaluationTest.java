package dfgg.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.application.item.ItemPurchaseTypeClassifier;
import dfgg.application.match.ParticipantItemPurchaseBundle;
import dfgg.application.match.ParticipantItemPurchaseSnapshotService;
import dfgg.application.match.PurchaseInventoryReconstructor;
import dfgg.application.recommend.component.ComponentCandidateFeatures;
import dfgg.application.recommend.v3.CandidateGenerator;
import dfgg.application.recommend.v3.CandidateTopK;
import dfgg.application.recommend.v3.CandidateUnion;
import dfgg.application.recommend.v3.HardValidityFilter;
import dfgg.application.recommend.v3.ranker.CandidateRanker;
import dfgg.application.recommend.v3.ranker.GradientBoostedTrees;
import dfgg.domain.item.Item;
import dfgg.domain.item.ItemRepository;
import dfgg.domain.match.BuildPathResolver;
import dfgg.domain.match.ItemPurchaseType;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import dfgg.infrastructure.external.catalog.DataDragonPurchaseItemCatalog;
import dfgg.infrastructure.external.dto.ItemResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** EC2에서 실제 모델·통계로 한 구매 시점의 연결을 확인한다. 학습 export/품질 평가는 별도다. */
@Tag("evaluation")
@ActiveProfiles("evaluation")
// 검증 DB에 미사용 엔티티(예: embeddings)의 테이블까지 요구하지 않는다. 필요한 조회는 아래에서 확인한다.
@SpringBootTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=none",
        "spring.datasource.hikari.read-only=true", "collection.scheduler.enabled=false"})
@Transactional(readOnly = true)
class ComponentConnectionEvaluationTest {
    @Autowired ParticipantItemPurchaseSnapshotService snapshots;
    @Autowired PurchaseInventoryReconstructor inventoryReconstructor;
    @Autowired NormalizedMatchParticipantRepository participants;
    @Autowired ItemRepository items;
    @Autowired List<CandidateGenerator> generators;
    @Autowired CandidateTopK retrievalLimits;
    @Autowired HardValidityFilter validity;
    @Autowired CandidateRanker coreRanker;
    @Autowired @Qualifier("componentLtrModel") GradientBoostedTrees componentModel;
    @Autowired ResourceLoader resources;
    @Autowired Environment environment;

    @Test
    void connect() throws Exception {
        var mapper = new ObjectMapper();
        String corePath = environment.getRequiredProperty("recommendation.ltr.model-path");
        var coreResource = resources.getResource(corePath.contains(":") ? corePath : "classpath:" + corePath);
        byte[] coreBytes;
        try (var input = coreResource.getInputStream()) { coreBytes = input.readAllBytes(); }
        assertThat(mapper.readTree(coreBytes).path("objective").asText())
                .as("임시 Core 모델로 연결 검증을 통과시키지 않는다")
                .isEqualTo("lambdarank");
        String componentPath = environment.getRequiredProperty("recommendation.component-ltr.model-path");
        byte[] componentBytes;
        try (var input = resources.getResource(componentPath).getInputStream()) {
            componentBytes = input.readAllBytes();
        }

        String matchId = required("evaluation.component.match-id");
        int participantId = Integer.parseInt(required("evaluation.component.participant-id"));
        int startOrder = Integer.parseInt(required("evaluation.component.purchase-order"));
        int topK = Integer.getInteger("evaluation.component.core-top-k", 5);
        if (topK < 1) throw new IllegalArgumentException("Core Top-K는 양수여야 합니다.");
        var response = mapper.readValue(Path.of(required("evaluation.component.catalog")).toFile(), ItemResponse.class);
        String version = response.dataVersion() == null ? response.version() : response.dataVersion();
        assertThat(version).matches("\\d+\\.\\d+\\.\\d+");
        String patch = version.substring(0, version.lastIndexOf('.'));
        ParticipantItemPurchaseBundle bundle = snapshots.bundlesForMatch(matchId).stream()
                .filter(b -> b.participantId() == participantId && b.startPurchaseOrder() == startOrder)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("지정한 시작 순번의 구매 묶음이 없습니다."));
        assertThat(bundle.ready()).as("구매 묶음 제외 사유: %s", bundle.exclusionReasons()).isTrue();
        assertThat(bundle.purchases().getFirst().purchase().getPatch()).isEqualTo(patch);
        var matchParticipants = participants.findByMatchId(matchId);
        assertThat(matchParticipants).as("Core 정규화 참가자와 팀 정보가 필요합니다").hasSize(10);

        // 사후 core_item_purchase_order는 읽지 않는다. 구매 이전 이벤트와 당시 보유 상태만 연결한다.
        var pastCoreIds = snapshots.snapshotsForMatch(matchId).stream()
                .filter(s -> s.purchase().getParticipantId() == participantId)
                .filter(s -> s.purchase().getPurchaseOrder() < startOrder)
                // 기존 Core 카탈로그·구매 순서에는 신발도 포함된다. 이벤트의 BOOTS 분류와 구분한다.
                .filter(s -> s.purchase().getPurchaseType() == ItemPurchaseType.CORE
                        || s.purchase().getPurchaseType() == ItemPurchaseType.BOOTS)
                .filter(s -> inventoryReconstructor.isStillOwned(s.purchase().getItemId(), bundle.inventoryBefore(), patch))
                .map(s -> Long.valueOf(s.purchase().getItemId())).distinct().toList();
        var query = new SnapshotQueryBuilder().buildAtPurchase(matchParticipants, participantId, patch, pastCoreIds);
        assertThat(query.position().isFullBuild(query.purchasedItemCount())).as("이미 최종 Core 빌드입니다").isFalse();

        // 기존 운영 Core와 동일한 generator·filter·Feature·ranker Bean을 사용하며 통계를 재집계하지 않는다.
        var union = CandidateUnion.merge(generators.stream()
                .map(g -> g.generate(query, retrievalLimits.of(g.source()))).toList());
        var itemIds = new ArrayList<>(union.candidates().stream().map(c -> c.itemId()).toList());
        itemIds.addAll(query.purchasedItemIds());
        var itemById = items.findAllById(itemIds).stream().collect(Collectors.toMap(Item::getItemId, Function.identity()));
        var topCores = coreRanker.rank(validity.filter(union, query.purchasedItemIds(), itemById), query, topK);
        assertThat(topCores).as("Core 후보가 없습니다. EC2의 아이템·추천 통계 테이블을 확인하세요").isNotEmpty();

        var catalog = new DataDragonPurchaseItemCatalog(response.data());
        var purchaseTypes = new ItemPurchaseTypeClassifier().classify(response.data());
        var componentRecommendations = new dfgg.application.recommend.component.ComponentRecommendationService(componentModel);
        var targetIds = topCores.stream().map(c -> Math.toIntExact(c.itemId())).toList();
        var branches = componentRecommendations.recommend(targetIds, bundle.inventoryBefore(), bundle.goldLower(),
                bundle.purchases().getFirst().sourceEvent().gameTimeMs(), query.position().name(), patch, response);
        var coreRows = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < topCores.size(); i++) {
            var core = topCores.get(i);
            var branch = branches.get(i);
            var row = new LinkedHashMap<String, Object>();
            row.put("itemId", branch.targetItemId());
            row.put("rank", i + 1);
            row.put("score", core.modelScore());
            row.put("component", branch);
            coreRows.add(row);
        }
        long candidateCount = branches.stream().mapToLong(b -> b.rankedPurchases().size()).sum();
        var report = new LinkedHashMap<String, Object>();
        report.put("purpose", "CONNECTION_SMOKE_ONLY");
        report.put("deployable", false); report.put("leakageValidation", "NOT_VERIFIED");
        report.put("coreModelSha256", sha256(coreBytes)); report.put("componentModelSha256", sha256(componentBytes));
        report.put("catalogSha256", sha256(Files.readAllBytes(Path.of(required("evaluation.component.catalog")))));
        report.put("dataVersion", version); report.put("coreQuery", query);
        report.put("matchId", matchId); report.put("participantId", participantId); report.put("startPurchaseOrder", startOrder);
        report.put("inventoryBefore", bundle.inventoryBefore().quantities());
        report.put("budget", bundle.goldLower()); report.put("budgetSource", "GOLD_RANGE_LOWER");
        report.put("observedItems", bundle.itemQuantities()); report.put("coreTopK", coreRows);
        report.put("candidateCount", candidateCount); report.put("targetRecommendations", branches);
        Path output = Path.of(required("evaluation.component.output"));
        Files.createDirectories(output);
        mapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve("connection-report.json").toFile(), report);
        System.out.printf("COMPONENT_CONNECTION matchId=%s participantId=%d purchaseOrder=%d cores=%d candidates=%d output=%s%n",
                matchId, participantId, startOrder, topCores.size(), candidateCount, output.resolve("connection-report.json"));
        assertThat(branches).hasSize(topCores.size());
    }

    private static String required(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("실행 옵션이 필요합니다: -D" + name);
        return value;
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
