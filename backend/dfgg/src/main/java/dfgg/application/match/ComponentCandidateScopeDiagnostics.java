package dfgg.application.match;

import dfgg.domain.match.ComponentTargetPolicy;
import dfgg.domain.match.PurchaseItemCatalog;
import dfgg.domain.match.TargetRecipePath;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 관측 구매의 경로 소속과 이후 빌드를 진단한다. 학습 후보·Label 생성에 결과를 전달하지 않는다. */
public final class ComponentCandidateScopeDiagnostics {
    private final PurchaseItemCatalog catalog;
    private final ComponentTargetPolicy targets;

    ComponentCandidateScopeDiagnostics(PurchaseItemCatalog catalog) {
        this.catalog = catalog;
        this.targets = new ComponentTargetPolicy(catalog);
    }

    Diagnostic inspect(ParticipantItemPurchaseBundle bundle, ComponentTrainingDataService.Example example,
                       List<ParticipantItemPurchaseSnapshot> purchases, Integer championId) {
        String skip = skipReason(bundle, example, purchases);
        if (skip != null) return result(bundle, example, championId, skip, null, List.of(), Map.of(), List.of());
        final TargetRecipePath path;
        final Map<Integer, Integer> outside;
        try {
            path = new TargetRecipePath(example.targetItemId(), catalog);
            outside = path.outsideItems(example.observedItems());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return result(bundle, example, championId, "UNRESOLVED_RECIPE_PATH", exception.getMessage(),
                    List.of(), Map.of(), List.of());
        }
        if (outside.isEmpty()) {
            return result(bundle, example, championId, "WITHIN_TARGET_PATH", null, path.itemIds(), outside, List.of());
        }
        return result(bundle, example, championId, "OUT_OF_TARGET_PATH_PURCHASE", null,
                path.itemIds(), outside, subsequentCompletions(bundle, purchases, outside));
    }

    private String skipReason(ParticipantItemPurchaseBundle bundle, ComponentTrainingDataService.Example example,
                              List<ParticipantItemPurchaseSnapshot> purchases) {
        if (bundle.exclusionReasons().stream().anyMatch(Set.of(
                ParticipantItemPurchaseBundle.Reason.PURCHASE_UNDONE,
                ParticipantItemPurchaseBundle.Reason.CANCELLATION_UNRESOLVED,
                ParticipantItemPurchaseBundle.Reason.UNDO_INCLUDED,
                ParticipantItemPurchaseBundle.Reason.EVENT_TIME_INVALID)::contains)) {
            return "UNRESOLVED_OR_CANCELLED_OBSERVATION";
        }
        if (bundle.purchases().stream().anyMatch(p -> p.sourceEvent() == null || gameTime(p) == null)) {
            return "UNRESOLVED_OBSERVATION_SOURCE";
        }
        if (example.targetItemId() == null) return "NO_OBSERVED_TARGET";
        if (example.targetPurchaseOrder() < bundle.endPurchaseOrder()) return "TARGET_BOUNDARY_CROSSED";
        if (purchases.stream().anyMatch(p -> p.purchase().getParticipantId() == bundle.participantId()
                && p.purchase().getPurchaseOrder() == example.targetPurchaseOrder()
                && p.reason() == ParticipantItemPurchaseSnapshot.Reason.CANCELLATION_UNRESOLVED)) {
            return "TARGET_CANCELLATION_UNRESOLVED";
        }
        return null;
    }

    private List<Completion> subsequentCompletions(ParticipantItemPurchaseBundle bundle,
            List<ParticipantItemPurchaseSnapshot> purchases, Map<Integer, Integer> outside) {
        List<Completion> completions = new ArrayList<>();
        for (var snapshot : purchases) {
            var purchase = snapshot.purchase();
            if (purchase.getParticipantId() != bundle.participantId()
                    || purchase.getPurchaseOrder() <= bundle.endPurchaseOrder()
                    || snapshot.reason() == ParticipantItemPurchaseSnapshot.Reason.PURCHASE_UNDONE
                    || snapshot.reason() == ParticipantItemPurchaseSnapshot.Reason.CANCELLATION_UNRESOLVED) continue;
            // CORE는 기존 구매 분류, BOOTS는 기존 목표 정책으로 완성 여부를 판단한다.
            try {
                if (!targets.isObservedTarget(purchase.getItemId(), purchase.getPurchaseType())) continue;
            } catch (IllegalStateException exception) {
                completions.add(completion(bundle, snapshot, "UNRESOLVED_COMPLETION_TYPE", List.of(), exception.getMessage()));
                continue;
            }
            try {
                var futurePath = new TargetRecipePath(purchase.getItemId(), catalog);
                var related = outside.keySet().stream().filter(id -> id != purchase.getItemId())
                        .filter(futurePath::contains).sorted().toList();
                completions.add(completion(bundle, snapshot, "RECIPE_MEMBERSHIP_ONLY", related, null));
            } catch (IllegalArgumentException | IllegalStateException exception) {
                completions.add(completion(bundle, snapshot, "UNRESOLVED_RECIPE_PATH", List.of(), exception.getMessage()));
            }
        }
        return List.copyOf(completions);
    }

    private Completion completion(ParticipantItemPurchaseBundle bundle, ParticipantItemPurchaseSnapshot snapshot,
                                  String evidence, List<Integer> related, String reason) {
        Long purchasedAt = gameTime(bundle.purchases().getFirst());
        Long completedAt = gameTime(snapshot);
        Long elapsed = purchasedAt == null || completedAt == null || completedAt < purchasedAt
                ? null : completedAt - purchasedAt;
        return new Completion(snapshot.purchase().getItemId(), snapshot.purchase().getPurchaseOrder(), completedAt,
                elapsed, evidence, related, reason);
    }

    private Diagnostic result(ParticipantItemPurchaseBundle bundle, ComponentTrainingDataService.Example example,
            Integer championId, String status, String reason, List<Integer> path, Map<Integer, Integer> outside,
            List<Completion> completions) {
        String queryId = String.join(":", example.matchId(), String.valueOf(example.participantId()),
                String.valueOf(example.startPurchaseOrder()), String.valueOf(example.endPurchaseOrder()),
                String.valueOf(example.targetItemId()));
        var next = completions.stream().filter(c -> !c.evidence().equals("UNRESOLVED_COMPLETION_TYPE"))
                .findFirst().orElse(null);
        return new Diagnostic("component-candidate-scope-v1", queryId, example.matchId(), example.participantId(),
                example.startPurchaseOrder(), example.endPurchaseOrder(), championId, example.position(),
                gameTime(bundle.purchases().getFirst()), example.dataVersion(), example.goldLower(), example.goldUpper(),
                example.inventoryBefore(), example.targetItemId(), example.observedItems(), outside,
                example.candidates().stream().map(c -> c.candidate().itemQuantities()).toList(),
                candidateSetStatus(example),
                path, example.inventoryBefore() == null ? null : example.completedCoreCount(),
                example.status(), example.exclusionReasons(), status, reason, next, completions);
    }

    private Long gameTime(ParticipantItemPurchaseSnapshot snapshot) {
        return snapshot.sourceEvent() == null ? null : snapshot.sourceEvent().gameTimeMs();
    }

    private String candidateSetStatus(ComponentTrainingDataService.Example example) {
        if (!example.candidates().isEmpty()) return "EXPORTED_TRAINING_CANDIDATES";
        if (Set.of("READY", "OBSERVED_BUNDLE_MISSING", "OBSERVED_COST_MISMATCH",
                "OBSERVED_COST_NOT_CONFIRMED", "OBSERVED_COST_OUTSIDE_GOLD_RANGE").contains(example.status())) {
            return "EXPORTED_EMPTY_CANDIDATE_SET";
        }
        return "NOT_GENERATED_OR_UNAVAILABLE";
    }

    public record Completion(int itemId, int purchaseOrder, Long gameTimeMs, Long elapsedSincePurchaseMs,
                      String evidence, List<Integer> relatedOutsideItemIds, String unresolvedReason) {}

    /** 기존 학습 Example과 분리된 출력 계약. 미래 정보는 이 진단 DTO에만 존재한다. */
    public record Diagnostic(String schemaVersion, String queryId, String matchId, int participantId,
                      int startPurchaseOrder, int endPurchaseOrder, Integer championId, String role,
                      Long gameTimeMs, String dataVersion, Integer goldLower, Integer goldUpper,
                      Map<Integer, Integer> inventoryBefore, Integer targetItemId,
                      Map<Integer, Integer> observedPurchaseBundle, Map<Integer, Integer> outsideTargetPathItems,
                      List<Map<Integer, Integer>> candidateSet, String candidateSetStatus, List<Integer> targetRecipePath,
                      Long completedCoreCount, String trainingStatus, List<String> bundleExclusionReasons,
                      String status, String unresolvedReason, Completion nextCompletedItem,
                      List<Completion> subsequentCompletedItems) {}
}
