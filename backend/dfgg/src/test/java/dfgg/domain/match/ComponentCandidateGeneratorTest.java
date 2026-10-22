package dfgg.domain.match;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ComponentCandidateGeneratorTest {
    private static final int TEAR = 3070, MANAMUNE = 3004, MURAMANA = 3042;
    // 4: 롱소드(1) 경로 목표, 6: 눈물이 레시피에 직접 들어가는 목표, 8: 눈물로 만드는 중간 재료(9)를 쓰는 목표.
    private final Map<Integer, PurchaseCatalogItem> items = Map.ofEntries(
            Map.entry(1, item(1, 350)), Map.entry(2, item(2, 400)), Map.entry(3, item(3, 900, 1)),
            Map.entry(4, item(4, 2000, 1, 3)), Map.entry(6, item(6, 1500, TEAR, 1)),
            Map.entry(9, item(9, 900, TEAR)), Map.entry(8, item(8, 2500, 9)),
            Map.entry(TEAR, item(TEAR, 400)), Map.entry(MANAMUNE, item(MANAMUNE, 2900, TEAR, 1)),
            Map.entry(MURAMANA, item(MURAMANA, 2900)));
    private final PurchaseItemCatalog catalog = id -> java.util.Optional.ofNullable(items.get(id));
    private final ComponentPolicyEvidence tear = new ComponentPolicyEvidence(TEAR, .85, 1062);

    private List<ComponentCandidateGenerator.Candidate> generate(int target, List<ComponentPolicyEvidence> policies,
                                                                ItemInventory inventory, long gold) {
        return new ComponentCandidateGenerator(catalog).generate(policies, target, inventory, gold);
    }

    private List<ComponentCandidateGenerator.Candidate> prepared(int target, ItemInventory inventory, long gold) {
        return generate(target, List.of(tear), inventory, gold).stream()
                .filter(c -> c.candidateSource() == ComponentCandidateGenerator.Source.PREPARED).toList();
    }

    @Test void prepared_item_is_bought_alone_and_combined_with_target_materials() {
        var candidates = prepared(4, ItemInventory.empty(), 750);
        assertThat(candidates).extracting(c -> c.bundle().itemQuantities())
                .containsExactlyInAnyOrder(Map.of(TEAR, 1), Map.of(TEAR, 1, 1, 1));
        var combined = candidates.stream().filter(c -> c.bundle().itemQuantities().size() == 2).findFirst().orElseThrow();
        assertThat(combined.bundle().additionalCost()).isEqualTo(750);
        assertThat(combined.bundle().purchaseOrder()).containsExactly(TEAR, 1);
        assertThat(combined.policyItemId()).isEqualTo(TEAR);
        assertThat(combined.policyFeatures()).containsExactly(1.0, .85, Math.log1p(1062));
    }

    @Test void same_budget_and_slot_rules_apply_to_prepared_candidates() {
        assertThat(prepared(4, ItemInventory.empty(), 399)).isEmpty();
        assertThat(prepared(4, ItemInventory.empty(), 749)).extracting(c -> c.bundle().itemQuantities())
                .containsExactly(Map.of(TEAR, 1));
        var full = ItemInventory.empty().add(2).add(2).add(2).add(2).add(2).add(2);
        assertThat(prepared(4, full, 5000)).isEmpty();
    }

    @Test void target_path_wins_on_identical_quantities() {
        assertThat(generate(6, List.of(tear), ItemInventory.empty(), 2000))
                .filteredOn(c -> c.bundle().itemQuantities().equals(Map.of(TEAR, 1))).singleElement()
                .satisfies(c -> assertThat(c.candidateSource()).isEqualTo(ComponentCandidateGenerator.Source.TARGET_PATH));
    }

    @Test void combined_candidate_does_not_recombine_the_prepared_item_just_purchased() {
        // 눈물 → 9(눈물 소비)는 900이지만, 재조합 없는 9 → 눈물은 1300이라 예산을 넘는다.
        assertThat(generate(8, List.of(tear), ItemInventory.empty(), 900))
                .noneMatch(c -> c.bundle().itemQuantities().equals(Map.of(TEAR, 1, 9, 1)));
    }

    @Test void prepared_item_is_skipped_once_its_line_is_started() {
        assertThat(prepared(4, ItemInventory.empty().add(TEAR), 1000)).isEmpty();
        assertThat(prepared(4, ItemInventory.empty().add(MANAMUNE), 1000)).isEmpty();
        assertThat(prepared(4, ItemInventory.empty().add(MURAMANA), 1000)).isEmpty();
    }

    @Test void no_policy_and_completed_targets_keep_target_path_behavior() {
        var targetOnly = generate(4, List.of(), ItemInventory.empty().add(2), 1000);
        assertThat(targetOnly).allMatch(c -> c.candidateSource() == ComponentCandidateGenerator.Source.TARGET_PATH);
        assertThat(generate(4, List.of(tear), ItemInventory.empty().add(4), 5000)).isEmpty();
    }

    @Test void out_of_path_purchase_preserves_target_progress_definition() {
        var before = ItemInventory.empty();
        var bought = prepared(4, before, 400).getFirst();
        var progress = new TargetItemProgress(catalog);
        assertThat(progress.measure(4, bought.bundle().inventoryAfter())).isEqualTo(progress.measure(4, before));
    }

    @Test void evidence_accepts_only_whitelisted_items() {
        assertThatThrownBy(() -> new ComponentPolicyEvidence(1036, .85, 1062)).isInstanceOf(IllegalArgumentException.class);
    }

    private static PurchaseCatalogItem item(int id, int price, Integer... ingredients) {
        return new PurchaseCatalogItem(id, price, true, List.of(ingredients), List.of());
    }
}
