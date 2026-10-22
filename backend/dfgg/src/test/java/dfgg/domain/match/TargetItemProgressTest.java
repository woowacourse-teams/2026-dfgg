package dfgg.domain.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TargetItemProgressTest {
    private final Map<Integer, PurchaseCatalogItem> items = Map.of(
            1, item(1, 350), 2, item(2, 875), 3, item(3, 1000, 1, 1),
            4, item(4, 3000, 3, 2), 5, item(5, 2500, 3, 3), 9, item(9, 100));
    private final PurchaseItemCatalog catalog = id -> Optional.ofNullable(items.get(id));
    private final TargetItemProgress progress = new TargetItemProgress(catalog);

    @Test
    void remaining_gold_uses_recipe_positions_and_not_unrelated_inventory_prices() {
        var state = ItemInventory.empty().add(3).add(1).add(9);
        assertThat(progress.measure(4, state).remainingGold()).isEqualTo(2000);
        assertThat(progress.measure(4, ItemInventory.empty().add(1)).remainingGold()).isEqualTo(2650);
    }

    @Test
    void coverage_expands_owned_intermediate_and_preserves_repeated_leaf_quantities() {
        assertThat(progress.measure(4, ItemInventory.empty().add(1)).materialCoverage()).isCloseTo(1.0 / 3, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(progress.measure(4, ItemInventory.empty().add(3).add(1)).materialCoverage()).isCloseTo(2.0 / 3, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(progress.measure(5, ItemInventory.empty().add(3).add(1)).materialCoverage()).isEqualTo(0.75);
        assertThat(progress.measure(5, ItemInventory.empty().add(3).add(1)).remainingGold()).isEqualTo(1150);
    }

    @Test
    void candidate_consumption_and_completion_use_the_resolver_inventory() {
        var before = ItemInventory.empty().add(1);
        var bundles = new BuildPathResolver(catalog).resolve(4, before, 3000);
        var intermediate = bundles.stream().filter(c -> c.itemQuantities().equals(Map.of(3, 1))).findFirst().orElseThrow();
        assertThat(intermediate.additionalCost()).isEqualTo(650);
        assertThat(progress.measure(4, intermediate.inventoryAfter()).remainingGold()).isEqualTo(2000);
        assertThat(progress.measure(4, intermediate.inventoryAfter()).fraction() - progress.measure(4, before).fraction())
                .isCloseTo(650.0 / 3000, org.assertj.core.data.Offset.offset(1e-12));
        var completed = bundles.stream().filter(c -> c.itemQuantities().equals(Map.of(4, 1))).findFirst().orElseThrow();
        var result = progress.measure(4, completed.inventoryAfter());
        assertThat(result.remainingGold()).isZero();
        assertThat(result.remainingRecipeDepth()).isZero();
        assertThat(result.materialCoverage()).isEqualTo(1);
        assertThat(result.fraction()).isEqualTo(1);
        assertThat(before).isEqualTo(ItemInventory.empty().add(1));
    }

    @Test
    void minimum_actual_combine_depth_is_one_for_direct_shop_completion_and_zero_when_owned() {
        assertThat(progress.measure(4, ItemInventory.empty()).remainingRecipeDepth()).isEqualTo(1);
        assertThat(progress.measure(4, ItemInventory.empty().add(1)).remainingRecipeDepth()).isEqualTo(1);
        assertThat(progress.measure(4, ItemInventory.empty().add(3).add(2)).remainingRecipeDepth()).isEqualTo(1);
        assertThat(progress.measure(4, ItemInventory.empty().add(4)).remainingRecipeDepth()).isZero();
    }

    @Test
    void unsupported_zero_price_missing_recipe_and_cycles_fail_explicitly() {
        assertThatThrownBy(() -> progress.measure(3097, ItemInventory.empty())).hasMessageContaining("UNSUPPORTED_CATALOG_ITEM");
        assertThatThrownBy(() -> progress.measure(4, ItemInventory.empty().add(3097))).isInstanceOf(IllegalStateException.class);
        var broken = new java.util.HashMap<>(items);
        broken.put(3, item(3, 1000, 4));
        assertThatThrownBy(() -> new TargetItemProgress(id -> Optional.ofNullable(broken.get(id))).measure(4, ItemInventory.empty()))
                .hasMessageContaining("순환");
        broken.put(3, item(3, 1000, 3097));
        assertThatThrownBy(() -> new TargetItemProgress(id -> Optional.ofNullable(broken.get(id))).measure(4, ItemInventory.empty()))
                .hasMessageContaining("UNSUPPORTED_CATALOG_ITEM");
        broken.put(4, item(4, 0));
        assertThatThrownBy(() -> new TargetItemProgress(id -> Optional.ofNullable(broken.get(id))).measure(4, ItemInventory.empty()))
                .hasMessageContaining("UNSUPPORTED_TARGET_MECHANIC");
    }

    @Test
    void equivalent_material_uses_the_same_variant_policy_as_candidate_generation() {
        var variants = new java.util.HashMap<>(items);
        variants.put(3, new PurchaseCatalogItem(3, 1000, true, List.of(1, 1), List.of(4)));
        variants.put(6, new PurchaseCatalogItem(6, 1000, true, List.of(1, 1), List.of(4)));
        var result = new TargetItemProgress(id -> Optional.ofNullable(variants.get(id))).measure(4, ItemInventory.empty().add(6));
        assertThat(result.remainingGold()).isEqualTo(2000);
        assertThat(result.materialCoverage()).isCloseTo(2.0 / 3, org.assertj.core.data.Offset.offset(1e-12));
    }

    private static PurchaseCatalogItem item(int id, int price, Integer... children) {
        return new PurchaseCatalogItem(id, price, true, List.of(children), List.of());
    }
}
