package dfgg.application.match;

import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.application.recommend.component.ComponentCandidateFeatures;
import dfgg.application.recommend.component.ComponentCompositionReader;
import dfgg.application.recommend.context.CompositionFeatures;
import dfgg.domain.match.ComponentCandidateGenerator;
import dfgg.domain.match.PreparationLookup;
import dfgg.application.ChampionPositionNormalizer;
import dfgg.domain.match.ComponentTargetPolicy;
import dfgg.domain.match.ItemPurchaseType;
import dfgg.domain.match.PurchaseItemCatalog;
import dfgg.infrastructure.external.catalog.DataDragonPurchaseItemCatalog;
import dfgg.infrastructure.external.dto.ItemResponse;
import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실제 미래 목표의 관측 구매를 조건으로 한 데이터 생성. 운영 Core 추천 성능 평가는 아니다.
 * READY 후보의 label은 관측 구매와 아이템별 수량이 같으면 1, 비관측 대안이면 0이다.
 * 이 구분은 구매 품질이나 승률의 우열을 뜻하지 않는다.
 */
@Service
public class ComponentTrainingDataService {
    private final ParticipantItemPurchaseSnapshotService snapshots;
    private final ObjectMapper mapper;
    private final PreparationLookup lookup;

    private final ComponentCompositionReader compositions;

    public ComponentTrainingDataService(ParticipantItemPurchaseSnapshotService snapshots, ObjectMapper mapper,
            PreparationLookup lookup, ComponentCompositionReader compositions) {
        this.lookup = lookup;
        this.snapshots = snapshots;
        this.mapper = mapper;
        this.compositions = compositions;
    }

    @Transactional(readOnly = true)
    public List<Example> examplesForMatch(String matchId, ItemResponse response) {
        return examples(prepareForMatch(matchId, response), response, Map.of());
    }

    /** 동일 스냅샷으로 기존 학습 행과 별도 Candidate 범위 진단을 만든다. */
    @Transactional(readOnly = true)
    public MatchDataset datasetForMatch(String matchId, ItemResponse response, Map<Integer, Integer> championIds) {
        var prepared = prepareForMatch(matchId, response);
        var examples = examples(prepared, response, championIds);
        var diagnostics = new ComponentCandidateScopeDiagnostics(new DataDragonPurchaseItemCatalog(response.data()));
        var rows = new java.util.ArrayList<ComponentCandidateScopeDiagnostics.Diagnostic>();
        for (int i = 0; i < prepared.bundles().size(); i++) {
            var bundle = prepared.bundles().get(i);
            rows.add(diagnostics.inspect(bundle, examples.get(i), prepared.snapshots(), championIds.get(bundle.participantId())));
        }
        return new MatchDataset(examples, List.copyOf(rows));
    }

    private ParticipantItemPurchaseSnapshotService.PreparedPurchases prepareForMatch(String matchId, ItemResponse response) {
        String version = response.dataVersion() == null ? response.version() : response.dataVersion();
        if (version == null || !version.matches("\\d+\\.\\d+\\.\\d+")) {
            throw new IllegalArgumentException("카탈로그의 정확한 dataVersion이 필요합니다.");
        }
        String patch = version.substring(0, version.lastIndexOf('.'));
        var prepared = snapshots.prepareForMatch(matchId);
        if (prepared.snapshots().stream().anyMatch(s -> !patch.equals(s.purchase().getPatch()))) {
            throw new IllegalArgumentException("경기와 카탈로그의 패치가 다릅니다.");
        }
        return prepared;
    }

    private List<Example> examples(ParticipantItemPurchaseSnapshotService.PreparedPurchases prepared, ItemResponse response, Map<Integer, Integer> championIds) {
        if (prepared.bundles().isEmpty()) return List.of();
        String version = response.dataVersion() == null ? response.version() : response.dataVersion();
        var catalog = new DataDragonPurchaseItemCatalog(response.data());
        lookup.validatePatch(version.substring(0, version.lastIndexOf('.')));
        if (lookup.enabled() && prepared.bundles().stream().anyMatch(b -> !championIds.containsKey(b.participantId()))) {
            throw new IllegalArgumentException("Preparation 학습에 참가자 Champion ID가 필요합니다.");
        }
        var resolver = new ComponentCandidateGenerator(catalog, lookup);
        var contexts = compositions.forMatch(prepared.bundles().getFirst().matchId());
        return prepared.bundles().stream()
                .map(bundle -> example(bundle, prepared.snapshots(), catalog, resolver, version, response, championIds.get(bundle.participantId()),
                        java.util.Objects.requireNonNull(contexts.get(bundle.participantId()), "구매 참가자의 경기 조합이 없습니다."))).toList();
    }

    /** 호출자가 Writer의 경로·수명·원자적 파일 교체를 관리한다. 제외 행도 보존해 분모를 확인한다. */
    @Transactional(readOnly = true)
    public void writeJsonl(String matchId, ItemResponse catalog, Writer output) throws IOException {
        for (var example : examplesForMatch(matchId, catalog)) {
            output.write(mapper.writeValueAsString(example));
            output.write('\n');
        }
    }

    private Example example(ParticipantItemPurchaseBundle bundle,
            List<ParticipantItemPurchaseSnapshot> purchases, PurchaseItemCatalog catalog,
            ComponentCandidateGenerator resolver, String version, ItemResponse response, Integer championId, CompositionFeatures composition) {
        // ponytail: 경기 내 선형 탐색. 대량 export에서 병목이 확인되면 참가자별 Core 순번 인덱스로 바꾼다.
        var targets = new ComponentTargetPolicy(catalog);
        var target = purchases.stream().filter(s -> s.purchase().getParticipantId() == bundle.participantId())
                .filter(s -> targets.isObservedTarget(s.purchase().getItemId(), s.purchase().getPurchaseType()))
                .filter(s -> s.purchase().getPurchaseOrder() >= bundle.startPurchaseOrder())
                .filter(s -> s.reason() != ParticipantItemPurchaseSnapshot.Reason.PURCHASE_UNDONE)
                .findFirst().orElse(null);
        if (!bundle.ready()) {
            return result(bundle, target, catalog, purchases, version, "BUNDLE_EXCLUDED", List.of());
        }
        if (target == null) {
            return result(bundle, null, catalog, purchases, version, "NO_OBSERVED_TARGET", List.of());
        }
        if (target.reason() == ParticipantItemPurchaseSnapshot.Reason.CANCELLATION_UNRESOLVED) {
            return result(bundle, target, catalog, purchases, version, "TARGET_CANCELLATION_UNRESOLVED", List.of());
        }
        if (target.purchase().getPurchaseOrder() < bundle.endPurchaseOrder()) {
            return result(bundle, target, catalog, purchases, version, "TARGET_BOUNDARY_CROSSED", List.of());
        }
        final List<ComponentCandidateGenerator.Candidate> candidates;
        try {
            targets.validate(target.purchase().getItemId(), bundle.inventoryBefore());
            String role = new ChampionPositionNormalizer().normalize(bundle.purchases().getFirst().purchase().getPosition())
                    .map(Enum::name).orElse("UNKNOWN");
            candidates = resolver.generate(championId, role, version.substring(0, version.lastIndexOf('.')),
                    target.purchase().getItemId(), bundle.inventoryBefore(), bundle.goldLower());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return result(bundle, target, catalog, purchases, version, "CANDIDATE_RESOLUTION_FAILED: " + exception.getMessage(), List.of());
        }
        var matched = candidates.stream().filter(c -> c.bundle().itemQuantities().equals(bundle.itemQuantities()))
                .findFirst().orElse(null);
        String status = matched == null ? "OBSERVED_BUNDLE_MISSING" : "READY";
        if (bundle.totalCost() > bundle.goldUpper()) {
            status = "OBSERVED_COST_OUTSIDE_GOLD_RANGE";
        } else if (bundle.totalCost() > bundle.goldLower()) {
            status = "OBSERVED_COST_NOT_CONFIRMED";
        } else if (matched != null && matched.bundle().additionalCost() != bundle.totalCost()) {
            status = "OBSERVED_COST_MISMATCH";
        }
        boolean usable = "READY".equals(status);
        final List<ComponentTrainingCandidate> rows;
        try {
            rows = candidates.stream().map(c -> new ComponentTrainingCandidate(
                    ComponentCandidateFeatures.calculate(bundle.inventoryBefore(), c, target.purchase().getItemId(), catalog, response,
                            bundle.purchases().getFirst().sourceEvent().gameTimeMs(), bundle.purchases().getFirst().purchase().getPosition(), composition),
                    usable ? (c.bundle().itemQuantities().equals(bundle.itemQuantities()) ? 1 : 0) : null, c.candidateSource(), c.preparationPatternCount(), c.preparationFutureRecipeLinkRate())).toList();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return result(bundle, target, catalog, purchases, version, "FEATURE_CALCULATION_FAILED: " + exception.getMessage(), List.of());
        }
        return result(bundle, target, catalog, purchases, version, status, rows);
    }

    private Example result(ParticipantItemPurchaseBundle bundle, ParticipantItemPurchaseSnapshot target,
                           PurchaseItemCatalog catalog, List<ParticipantItemPurchaseSnapshot> purchases,
                           String version, String status, List<ComponentTrainingCandidate> candidates) {
        var first = bundle.purchases().getFirst().purchase();
        return new Example(SCHEMA_VERSION, "OBSERVED_FUTURE_TARGET_ITEM", version,
                bundle.matchId(), bundle.participantId(), bundle.startPurchaseOrder(), bundle.endPurchaseOrder(),
                first.getPosition(), target == null ? null : target.purchase().getItemId(),
                target == null ? null : target.purchase().getPurchaseOrder(),
                bundle.inventoryBefore() == null ? null : bundle.inventoryBefore().quantities(),
                bundle.goldLower(), bundle.goldUpper(), bundle.itemQuantities(), bundle.totalCost(), status,
                bundle.exclusionReasons().stream().map(Enum::name).sorted().toList(), ComponentCandidateFeatures.FEATURE_NAMES, candidates,
                targetKind(target, catalog), completedCoreCount(bundle, purchases));
    }

    private String targetKind(ParticipantItemPurchaseSnapshot target, PurchaseItemCatalog catalog) {
        if (target == null) return null;
        try {
            return new ComponentTargetPolicy(catalog).kind(target.purchase().getItemId()).name();
        } catch (IllegalStateException exception) {
            return "UNSUPPORTED";
        }
    }

    /** 평가 메타데이터만 기록한다. Feature/학습 분할/모델 선택에는 사용하지 않는다. */
    private long completedCoreCount(ParticipantItemPurchaseBundle bundle, List<ParticipantItemPurchaseSnapshot> purchases) {
        if (bundle.inventoryBefore() == null) return 0;
        return purchases.stream().filter(s -> s.purchase().getParticipantId() == bundle.participantId())
                .filter(s -> s.purchase().getPurchaseOrder() < bundle.startPurchaseOrder())
                .filter(s -> s.purchase().getPurchaseType() == ItemPurchaseType.CORE)
                .filter(s -> s.reason() != ParticipantItemPurchaseSnapshot.Reason.PURCHASE_UNDONE)
                .map(s -> s.purchase().getItemId()).distinct()
                .filter(id -> bundle.inventoryBefore().quantityOf(id) > 0).count();
    }

    public static final String SCHEMA_VERSION = "component-bundle-validation-v7";
    public record MatchDataset(List<Example> examples, List<ComponentCandidateScopeDiagnostics.Diagnostic> diagnostics) {}
    public record Example(String schemaVersion, String targetSource, String dataVersion, String matchId,
                          int participantId, int startPurchaseOrder, int endPurchaseOrder, String position,
                          Integer targetItemId, Integer targetPurchaseOrder, Map<Integer, Integer> inventoryBefore,
                          Integer goldLower, Integer goldUpper, Map<Integer, Integer> observedItems, Long observedCost,
                          String status, List<String> exclusionReasons, List<String> featureNames,
                          List<ComponentTrainingCandidate> candidates, String targetKind, long completedCoreCount) {
        public Integer targetCoreId() { return targetItemId; }
    }
}
