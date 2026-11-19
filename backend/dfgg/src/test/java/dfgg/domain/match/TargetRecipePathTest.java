package dfgg.domain.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TargetRecipePathTest {
    private final Map<Integer, PurchaseCatalogItem> items = Map.of(
            1, item(1, 100, List.of(), List.of(3, 4)),
            2, item(2, 200, List.of(), List.of()),
            3, item(3, 300, List.of(1, 1), List.of(5)),
            4, item(4, 300, List.of(1, 1), List.of(5)),
            5, item(5, 500, List.of(3), List.of()));
    private final PurchaseItemCatalog catalog = id -> Optional.ofNullable(items.get(id));

    @Test
    void entire_path_includes_target_and_nested_materials_independent_of_quantity() {
        var path = new TargetRecipePath(5, catalog);
        assertThat(path.itemIds()).containsExactly(1, 3, 5);
        assertThat(path.outsideItems(Map.of(1, 10, 5, 1))).isEmpty();
    }

    @Test
    void mixed_bundle_reports_only_outside_items_with_original_quantities() {
        assertThat(new TargetRecipePath(5, catalog).outsideItems(Map.of(1, 2, 2, 3)))
                .containsExactlyEntriesOf(Map.of(2, 3));
    }

    @Test
    void equivalent_material_uses_existing_recipe_matcher() {
        assertThat(new TargetRecipePath(5, catalog).contains(4)).isTrue();
    }

    @Test
    void missing_catalog_is_unresolved_instead_of_outside() {
        assertThatThrownBy(() -> new TargetRecipePath(5, catalog).contains(999))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("UNSUPPORTED_CATALOG_ITEM");
    }

    @Test
    void cyclic_recipe_is_unresolved() {
        PurchaseItemCatalog cyclic = id -> Optional.of(item(id, 100, List.of(id), List.of()));
        assertThatThrownBy(() -> new TargetRecipePath(1, cyclic)).isInstanceOf(IllegalStateException.class);
    }

    private static PurchaseCatalogItem item(int id, int price, List<Integer> from, List<Integer> into) {
        return new PurchaseCatalogItem(id, price, true, from, into);
    }
}
