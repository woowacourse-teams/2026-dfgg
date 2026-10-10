package dfgg.domain.match;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ComponentCandidateGeneratorTest {
    private final Map<Integer, PurchaseCatalogItem> items = Map.of(
            1, item(1, 350), 2, item(2, 400), 3, item(3, 900, 1),
            4, item(4, 2000, 1, 3), 5, item(5, 600, 2),
            6, new PurchaseCatalogItem(6, 100, false, List.of(), List.of()),
            7, item(7, 1000, 6));
    private final PurchaseItemCatalog catalog = id -> java.util.Optional.ofNullable(items.get(id));
    private PreparationLookup lookup(int... ids) {
        return new PreparationLookup("16.18", "a".repeat(64), Map.of(new PreparationLookup.Key(81, "BOTTOM", 4),
                java.util.Arrays.stream(ids).mapToObj(id -> new PreparationLookup.Pattern(id, 50, .8, 200, 40)).toList()));
    }
    private List<ComponentCandidateGenerator.Candidate> generate(PreparationLookup lookup, ItemInventory inventory, long gold) {
        return new ComponentCandidateGenerator(catalog, lookup).generate(81, "BOTTOM", "16.18", 4, inventory, gold);
    }
    @Test void same_purchase_rules_apply_to_preparation_and_target_candidates() {
        assertThat(generate(lookup(2), ItemInventory.empty(), 399)).allMatch(c -> c.candidateSource() == ComponentCandidateGenerator.Source.TARGET_PATH);
        var candidate = generate(lookup(2), ItemInventory.empty(), 400).getLast();
        assertThat(candidate.bundle().itemQuantities()).isEqualTo(Map.of(2, 1));
        assertThat(candidate.bundle().additionalCost()).isEqualTo(400);
        assertThat(candidate.preparationPatternCount()).isEqualTo(50);
        var full = ItemInventory.empty().add(1).add(1).add(1).add(1).add(1).add(1);
        assertThat(generate(lookup(2), full, 1000)).allMatch(c -> c.candidateSource() == ComponentCandidateGenerator.Source.TARGET_PATH);
        var combining = full.remove(1).orElseThrow().add(2);
        assertThat(generate(lookup(5), combining, 200)).anyMatch(c -> c.bundle().itemQuantities().equals(Map.of(5, 1)) && c.bundle().additionalCost() == 200);
    }
    @Test void target_source_wins_and_preparation_is_single_purchase_only() {
        var candidates = generate(lookup(1, 5), ItemInventory.empty(), 2000);
        assertThat(candidates.stream().filter(c -> c.bundle().itemQuantities().equals(Map.of(1, 1))))
                .singleElement().satisfies(c -> assertThat(c.candidateSource()).isEqualTo(ComponentCandidateGenerator.Source.TARGET_PATH));
        assertThat(candidates.stream().filter(c -> c.candidateSource() == ComponentCandidateGenerator.Source.PREPARATION))
                .singleElement().satisfies(c -> assertThat(c.bundle().itemQuantities()).isEqualTo(Map.of(5, 1)));
    }
    @Test void missing_unsupported_and_owned_preparation_are_skipped_without_losing_target_path() {
        var expected = generate(PreparationLookup.disabled(), ItemInventory.empty().add(2), 1000);
        assertThat(generate(lookup(2, 7, 999), ItemInventory.empty().add(2), 1000)).isEqualTo(expected);
    }
    @Test void lookup_is_patch_and_context_specific() {
        var generator = new ComponentCandidateGenerator(catalog, lookup(2));
        assertThatThrownBy(() -> generator.generate(81, "BOTTOM", "16.19", 4, ItemInventory.empty(), 1000)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> generator.generate(null, "BOTTOM", "16.18", 4, ItemInventory.empty(), 1000)).isInstanceOf(IllegalArgumentException.class);
        assertThat(generator.generate(82, "BOTTOM", "16.18", 4, ItemInventory.empty(), 1000))
                .allMatch(c -> c.candidateSource() == ComponentCandidateGenerator.Source.TARGET_PATH);
        assertThat(generate(lookup(2), ItemInventory.empty().add(4), 1000)).isEmpty();
    }
    @Test void out_of_path_purchase_preserves_target_progress_definition() {
        var before = ItemInventory.empty();
        var prep = generate(lookup(2), before, 400).getLast();
        var progress = new TargetItemProgress(catalog);
        assertThat(progress.measure(4, prep.bundle().inventoryAfter())).isEqualTo(progress.measure(4, before));
    }
    private static PurchaseCatalogItem item(int id, int price, Integer... ingredients) {
        return new PurchaseCatalogItem(id, price, true, List.of(ingredients), List.of());
    }
}
