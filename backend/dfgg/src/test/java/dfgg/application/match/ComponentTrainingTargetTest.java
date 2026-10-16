package dfgg.application.match;

import static dfgg.application.recommend.context.TestCompositions.NO_TAGS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.domain.match.*;
import dfgg.infrastructure.external.dto.ItemResponse;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ComponentTrainingTargetTest {
    private final ParticipantItemPurchaseSnapshotService snapshots = mock(ParticipantItemPurchaseSnapshotService.class);
    private final dfgg.application.recommend.component.ComponentCompositionReader compositions = mock(dfgg.application.recommend.component.ComponentCompositionReader.class);
    private final ComponentTrainingDataService exporter = new ComponentTrainingDataService(snapshots, new ObjectMapper(), ComponentPolicyLookup.disabled(), compositions);

    @org.junit.jupiter.api.BeforeEach
    void composition() {
        when(compositions.forMatch("KR_TEST")).thenReturn(java.util.Map.of(1, NO_TAGS, 2, NO_TAGS));
    }

    @Test
    void basic_boot_purchase_uses_only_observed_future_tier_two_boot_target() throws Exception {
        var basic = snapshot(1001, ItemPurchaseType.BOOTS, 1, 300, ItemInventory.empty());
        var armor = snapshot(1029, ItemPurchaseType.COMPONENT, 2, 300, ItemInventory.empty().add(1001));
        var finalBoots = snapshot(3047, ItemPurchaseType.BOOTS, 3, 600, ItemInventory.empty().add(1001).add(1029));
        prepare(List.of(basic, armor, finalBoots));
        var rows = exporter.examplesForMatch("KR_TEST", catalog());
        assertThat(rows).hasSize(3).allMatch(row -> row.status().equals("READY"));
        assertThat(rows).allMatch(row -> row.targetItemId() == 3047 && row.targetKind().equals("TIER_TWO_BOOTS"));
        assertThat(rows).allMatch(row -> row.candidates().stream().filter(c -> c.label() == 1).count() == 1);
        assertThat(rows.getFirst().candidates().stream().filter(c -> c.label() == 1).findFirst().orElseThrow()
                .candidate().itemQuantities()).containsOnlyKeys(1001);
    }

    @Test
    void support_observed_selection_is_supported_only_with_replayed_precursor() throws Exception {
        var supported = snapshot(3869, ItemPurchaseType.CORE, 1, 0, ItemInventory.empty().add(3867));
        prepare(List.of(supported));
        var row = exporter.examplesForMatch("KR_TEST", catalog()).getFirst();
        assertThat(row.status()).isEqualTo("BUNDLE_EXCLUDED");
        assertThat(row.targetKind()).isEqualTo("SUPPORT_FINISHED");
        assertThat(row.exclusionReasons()).contains("COST_UNKNOWN");
        assertThat(row.candidates()).isEmpty();
        var unknownAcquisition = snapshot(3869, ItemPurchaseType.CORE, 1, 400, ItemInventory.empty());
        prepare(List.of(unknownAcquisition));
        var excluded = exporter.examplesForMatch("KR_TEST", catalog()).getFirst();
        assertThat(excluded.status()).contains("UNSUPPORTED_TARGET_MECHANIC");
        assertThat(excluded.candidates()).isEmpty();
    }

    @Test
    void future_unobserved_targets_never_receive_pseudo_labels_and_basic_boot_is_not_a_target() throws Exception {
        prepare(List.of(snapshot(1001, ItemPurchaseType.BOOTS, 1, 300, ItemInventory.empty())));
        var row = exporter.examplesForMatch("KR_TEST", catalog()).getFirst();
        assertThat(row.status()).isEqualTo("NO_OBSERVED_TARGET");
        assertThat(row.targetItemId()).isNull();
        assertThat(row.candidates()).isEmpty();
    }

    @Test
    void all_core_stages_use_the_same_feature_contract_and_only_stage_metadata_changes() throws Exception {
        var first = snapshot(500, ItemPurchaseType.CORE, 1, 1000, ItemInventory.empty());
        var second = snapshot(501, ItemPurchaseType.CORE, 2, 1000, ItemInventory.empty().add(500));
        prepare(List.of(first, second));
        var catalog = new ObjectMapper().readValue("""
                {"version":"16.18.1","data":{
                 "1029":{"gold":{"total":300,"purchasable":true}},
                 "500":{"from":["1029"],"gold":{"total":1000,"purchasable":true}},
                 "501":{"from":["1029"],"gold":{"total":1000,"purchasable":true}}}}
                """, ItemResponse.class);
        var rows = exporter.examplesForMatch("KR_TEST", catalog);
        assertThat(rows).hasSize(2).allMatch(row -> row.status().equals("READY"));
        assertThat(rows).extracting(ComponentTrainingDataService.Example::completedCoreCount).containsExactly(0L, 1L);
        assertThat(rows.getFirst().featureNames()).isEqualTo(rows.getLast().featureNames());
        assertThat(rows.getFirst().featureNames()).hasSize(58).doesNotContain("completedCoreCount", "completed_core_count", "target_item_id");
        assertThat(rows).allMatch(row -> row.candidates().stream().filter(c -> c.label() == 1).count() == 1);
    }

    private void prepare(List<ParticipantItemPurchaseSnapshot> purchases) {
        when(snapshots.prepareForMatch("KR_TEST")).thenReturn(new ParticipantItemPurchaseSnapshotService.PreparedPurchases(
                purchases, purchases.stream().map(s -> new ParticipantItemPurchaseBundle(List.of(s), s.purchase().getItemCost() == null ? Set.of(ParticipantItemPurchaseBundle.Reason.COST_UNKNOWN) : Set.of())).toList()));
    }

    @Test
    void scope_diagnostic_keeps_training_examples_identical_and_records_future_recipe_connection() throws Exception {
        var preparation = snapshot(2, ItemPurchaseType.COMPONENT, 1, 200, ItemInventory.empty());
        var target = snapshot(500, ItemPurchaseType.CORE, 2, 1000, ItemInventory.empty().add(2));
        var future = snapshot(501, ItemPurchaseType.CORE, 3, 800, ItemInventory.empty().add(2).add(500));
        prepare(List.of(preparation, target, future));
        var response = preparationCatalog();
        var original = exporter.examplesForMatch("KR_TEST", response);
        var dataset = exporter.datasetForMatch("KR_TEST", response, java.util.Map.of(1, 81));
        assertThat(dataset.examples()).isEqualTo(original);
        var row = dataset.diagnostics().getFirst();
        assertThat(row.status()).isEqualTo("OUT_OF_TARGET_PATH_PURCHASE");
        assertThat(row.championId()).isEqualTo(81);
        assertThat(row.outsideTargetPathItems()).containsExactlyEntriesOf(java.util.Map.of(2, 1));
        assertThat(row.targetRecipePath()).containsExactly(1, 500);
        assertThat(row.candidateSet()).noneMatch(candidate -> candidate.containsKey(2));
        assertThat(dataset.examples().getFirst().candidates()).allMatch(candidate -> candidate.label() == null);
        assertThat(row.nextCompletedItem().itemId()).isEqualTo(500);
        assertThat(row.subsequentCompletedItems()).hasSize(2);
        assertThat(row.subsequentCompletedItems().getLast().relatedOutsideItemIds()).containsExactly(2);
        assertThat(row.subsequentCompletedItems().getLast().elapsedSincePurchaseMs()).isEqualTo(40_000);
        assertThat(row.subsequentCompletedItems().getLast().evidence()).isEqualTo("RECIPE_MEMBERSHIP_ONLY");
    }

    @Test
    void in_path_purchase_missing_due_to_budget_is_not_a_preparation_purchase() throws Exception {
        var material = snapshot(1, ItemPurchaseType.COMPONENT, 1, 300, ItemInventory.empty());
        material.purchase().recordGoldRange(new GoldRange(0, 100), GoldRangeStatus.BASE);
        prepare(List.of(material, snapshot(500, ItemPurchaseType.CORE, 2, 1000, ItemInventory.empty().add(1))));
        var dataset = exporter.datasetForMatch("KR_TEST", preparationCatalog(), java.util.Map.of());
        assertThat(dataset.examples().getFirst().candidates()).isEmpty();
        assertThat(dataset.diagnostics().getFirst().status()).isEqualTo("WITHIN_TARGET_PATH");
        assertThat(dataset.diagnostics().getFirst().candidateSetStatus()).isEqualTo("EXPORTED_EMPTY_CANDIDATE_SET");
    }

    @Test
    void unknown_observed_catalog_item_is_unresolved_not_preparation() throws Exception {
        prepare(List.of(snapshot(999, ItemPurchaseType.COMPONENT, 1, 200, ItemInventory.empty()),
                snapshot(500, ItemPurchaseType.CORE, 2, 1000, ItemInventory.empty())));
        var row = exporter.datasetForMatch("KR_TEST", preparationCatalog(), java.util.Map.of()).diagnostics().getFirst();
        assertThat(row.status()).isEqualTo("UNRESOLVED_RECIPE_PATH");
        assertThat(row.unresolvedReason()).contains("999");
        assertThat(row.championId()).isNull();
    }

    @Test
    void cancelled_future_completion_is_not_evidence_for_preparation() throws Exception {
        var future = snapshot(501, ItemPurchaseType.CORE, 3, 800, ItemInventory.empty().add(2));
        var cancelled = new ParticipantItemPurchaseSnapshot(future.purchase(), future.sourceEvent(), future.inventoryBefore(),
                ParticipantItemPurchaseSnapshot.Status.EXCLUDED, ParticipantItemPurchaseSnapshot.Reason.PURCHASE_UNDONE);
        prepare(List.of(snapshot(2, ItemPurchaseType.COMPONENT, 1, 200, ItemInventory.empty()),
                snapshot(500, ItemPurchaseType.CORE, 2, 1000, ItemInventory.empty().add(2)), cancelled));
        var row = exporter.datasetForMatch("KR_TEST", preparationCatalog(), java.util.Map.of()).diagnostics().getFirst();
        assertThat(row.subsequentCompletedItems()).extracting(ComponentCandidateScopeDiagnostics.Completion::itemId)
                .containsExactly(500);
    }

    @Test
    void excluded_mixed_bundle_is_diagnosed_without_changing_training_status() throws Exception {
        var material = snapshot(1, ItemPurchaseType.COMPONENT, 1, 300, ItemInventory.empty());
        var outside = snapshot(2, ItemPurchaseType.COMPONENT, 2, 200, ItemInventory.empty().add(1));
        var target = snapshot(500, ItemPurchaseType.CORE, 3, 1000, ItemInventory.empty());
        var bundle = new ParticipantItemPurchaseBundle(List.of(material, outside), Set.of(ParticipantItemPurchaseBundle.Reason.COST_UNKNOWN));
        when(snapshots.prepareForMatch("KR_TEST")).thenReturn(new ParticipantItemPurchaseSnapshotService.PreparedPurchases(
                List.of(material, outside, target), List.of(bundle)));
        var dataset = exporter.datasetForMatch("KR_TEST", preparationCatalog(), java.util.Map.of());
        assertThat(dataset.examples().getFirst().status()).isEqualTo("BUNDLE_EXCLUDED");
        var row = dataset.diagnostics().getFirst();
        assertThat(row.status()).isEqualTo("OUT_OF_TARGET_PATH_PURCHASE");
        assertThat(row.outsideTargetPathItems()).containsExactlyEntriesOf(java.util.Map.of(2, 1));
        assertThat(row.candidateSetStatus()).isEqualTo("NOT_GENERATED_OR_UNAVAILABLE");
    }

    @Test
    void undo_bundle_is_not_counted_as_preparation() throws Exception {
        var outside = snapshot(2, ItemPurchaseType.COMPONENT, 1, 200, ItemInventory.empty());
        var target = snapshot(500, ItemPurchaseType.CORE, 2, 1000, ItemInventory.empty());
        when(snapshots.prepareForMatch("KR_TEST")).thenReturn(new ParticipantItemPurchaseSnapshotService.PreparedPurchases(
                List.of(outside, target), List.of(new ParticipantItemPurchaseBundle(List.of(outside),
                Set.of(ParticipantItemPurchaseBundle.Reason.UNDO_INCLUDED)))));
        var row = exporter.datasetForMatch("KR_TEST", preparationCatalog(), java.util.Map.of()).diagnostics().getFirst();
        assertThat(row.status()).isEqualTo("UNRESOLVED_OR_CANCELLED_OBSERVATION");
    }

    @Test
    void cancelled_target_is_reported_even_when_bundle_was_excluded_first() throws Exception {
        var outside = snapshot(2, ItemPurchaseType.COMPONENT, 1, 200, ItemInventory.empty());
        var target = snapshot(500, ItemPurchaseType.CORE, 2, 1000, ItemInventory.empty());
        var unresolved = new ParticipantItemPurchaseSnapshot(target.purchase(), target.sourceEvent(), target.inventoryBefore(),
                ParticipantItemPurchaseSnapshot.Status.UNRESOLVED, ParticipantItemPurchaseSnapshot.Reason.CANCELLATION_UNRESOLVED);
        when(snapshots.prepareForMatch("KR_TEST")).thenReturn(new ParticipantItemPurchaseSnapshotService.PreparedPurchases(
                List.of(outside, unresolved), List.of(new ParticipantItemPurchaseBundle(List.of(outside),
                Set.of(ParticipantItemPurchaseBundle.Reason.COST_UNKNOWN)))));
        var dataset = exporter.datasetForMatch("KR_TEST", preparationCatalog(), java.util.Map.of());
        assertThat(dataset.examples().getFirst().status()).isEqualTo("BUNDLE_EXCLUDED");
        assertThat(dataset.diagnostics().getFirst().status()).isEqualTo("TARGET_CANCELLATION_UNRESOLVED");
    }

    @Test
    void another_participants_completion_is_not_linked() throws Exception {
        var otherEvent = new ParticipantItemEvent("KR_TEST", 2, 3, 3, 0, 3, "ITEM_PURCHASED", 501,
                null, null, 60_000L, "{}", "test");
        var otherPurchase = new NormalizedMatchParticipantItemEvent(otherEvent, ItemPurchaseType.CORE, "16.18", "MID");
        var other = new ParticipantItemPurchaseSnapshot(otherPurchase, otherEvent, ItemInventory.empty(),
                ParticipantItemPurchaseSnapshot.Status.READY, null);
        var outside = snapshot(2, ItemPurchaseType.COMPONENT, 1, 200, ItemInventory.empty());
        var target = snapshot(500, ItemPurchaseType.CORE, 2, 1000, ItemInventory.empty());
        when(snapshots.prepareForMatch("KR_TEST")).thenReturn(new ParticipantItemPurchaseSnapshotService.PreparedPurchases(
                List.of(outside, target, other), List.of(new ParticipantItemPurchaseBundle(List.of(outside), Set.of()))));
        var row = exporter.datasetForMatch("KR_TEST", preparationCatalog(), java.util.Map.of()).diagnostics().getFirst();
        assertThat(row.subsequentCompletedItems()).extracting(ComponentCandidateScopeDiagnostics.Completion::itemId)
                .containsExactly(500);
    }

    @Test
    void policy_candidates_are_identical_in_training_and_serving_regardless_of_observation() throws Exception {
        var lookup = new dfgg.domain.match.ComponentPolicyLookup("16.18", "a".repeat(64), java.util.Map.of(
                new dfgg.domain.match.ComponentPolicyLookup.RoleKey(81, "TOP"), new dfgg.domain.match.ComponentPolicyLookup.Prepared(1062, .85)));
        var training = new ComponentTrainingDataService(snapshots, new ObjectMapper(), lookup, compositions);
        var model = mock(dfgg.application.recommend.v3.ranker.GradientBoostedTrees.class);
        var serving = new dfgg.application.recommend.component.ComponentRecommendationService(model, lookup);
        var response = preparationCatalog();
        var served = serving.recommend(81, List.of(500), ItemInventory.empty(), 1200, 20_000,
                "TOP", "16.18", response, NO_TAGS).getFirst().rankedPurchases();
        for (int observed : List.of(1, 3070)) {
            prepare(List.of(snapshot(observed, ItemPurchaseType.COMPONENT, 1, observed == 1 ? 300 : 200, ItemInventory.empty()),
                    snapshot(500, ItemPurchaseType.CORE, 2, 1000, ItemInventory.empty().add(observed))));
            var row = training.datasetForMatch("KR_TEST", response, java.util.Map.of(1, 81)).examples().getFirst();
            assertThat(row.status()).isEqualTo("READY");
            assertThat(row.candidates()).extracting(ComponentTrainingCandidate::candidate)
                    .containsExactlyElementsOf(served.stream().map(c -> c.candidate()).toList());
            for (var candidate : row.candidates()) {
                var evidence = candidate.candidate().features().subList(55, 58);
                if (candidate.candidateSource() == dfgg.domain.match.ComponentCandidateGenerator.Source.PREPARED) {
                    assertThat(evidence).containsExactly(1.0, .85, Math.log1p(1062));
                } else {
                    assertThat(evidence).containsExactly(0.0, 0.0, 0.0);
                }
            }
            assertThat(row.candidates()).extracting(ComponentTrainingCandidate::candidateSource)
                    .containsExactlyElementsOf(served.stream().map(c -> c.candidateSource()).toList());
            assertThat(row.candidates().stream().filter(c -> c.label() == 1)).singleElement()
                    .satisfies(c -> assertThat(c.candidate().itemQuantities()).isEqualTo(java.util.Map.of(observed, 1)));
        }
    }

    private ItemResponse preparationCatalog() throws Exception {
        return new ObjectMapper().readValue("""
                {"version":"16.18.1","data":{
                 "1":{"gold":{"total":300,"purchasable":true}},
                 "2":{"gold":{"total":200,"purchasable":true}},
                 "3070":{"gold":{"total":200,"purchasable":true}},
                 "500":{"from":["1"],"gold":{"total":1000,"purchasable":true}},
                 "501":{"from":["2"],"gold":{"total":1000,"purchasable":true}}}}
                """, ItemResponse.class);
    }

    private ParticipantItemPurchaseSnapshot snapshot(int id, ItemPurchaseType type, int order, int cost, ItemInventory before) {
        var event = new ParticipantItemEvent("KR_TEST", 1, order, order, 0, order, "ITEM_PURCHASED", id, null, null,
                order * 20_000L, "{}", "test");
        var purchase = new NormalizedMatchParticipantItemEvent(event, type, "16.18", "TOP");
        if (cost > 0) purchase.recordCostHypothesis(cost);
        purchase.recordGoldRange(new GoldRange(1200, 1500), GoldRangeStatus.BASE);
        return new ParticipantItemPurchaseSnapshot(purchase, event, before, ParticipantItemPurchaseSnapshot.Status.READY, null);
    }

    private ItemResponse catalog() throws Exception {
        return new ObjectMapper().readValue("""
                {"version":"16.18.1","data":{
                  "1001":{"tags":["Boots"],"gold":{"total":300,"purchasable":true}},
                  "1029":{"gold":{"total":300,"purchasable":true}},
                  "3047":{"tags":["Boots"],"from":["1001","1029"],"gold":{"total":1200,"purchasable":true}},
                  "3867":{"gold":{"total":400,"purchasable":false}},
                  "3869":{"from":["3867"],"gold":{"total":400,"purchasable":true}}}}
                """, ItemResponse.class);
    }
}
