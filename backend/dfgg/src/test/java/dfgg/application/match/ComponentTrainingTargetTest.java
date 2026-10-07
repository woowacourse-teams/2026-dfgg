package dfgg.application.match;

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
    private final ComponentTrainingDataService exporter = new ComponentTrainingDataService(snapshots, new ObjectMapper());

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
        assertThat(rows.getFirst().featureNames()).hasSize(46).doesNotContain("completedCoreCount", "completed_core_count", "target_item_id");
        assertThat(rows).allMatch(row -> row.candidates().stream().filter(c -> c.label() == 1).count() == 1);
    }

    private void prepare(List<ParticipantItemPurchaseSnapshot> purchases) {
        when(snapshots.prepareForMatch("KR_TEST")).thenReturn(new ParticipantItemPurchaseSnapshotService.PreparedPurchases(
                purchases, purchases.stream().map(s -> new ParticipantItemPurchaseBundle(List.of(s), s.purchase().getItemCost() == null ? Set.of(ParticipantItemPurchaseBundle.Reason.COST_UNKNOWN) : Set.of())).toList()));
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
