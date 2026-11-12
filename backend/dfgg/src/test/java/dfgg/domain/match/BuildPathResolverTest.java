package dfgg.domain.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BuildPathResolverTest {
    // 가상 카탈로그: Core 3000 = 중간품 1000(롱소드 350 두 개) + 곡괭이 875 + 조합비.
    private final Map<Integer, PurchaseCatalogItem> items = Map.of(
            1, item(1, 350), 2, item(2, 875), 3, item(3, 1000, 1, 1),
            4, item(4, 3000, 3, 2), 9, item(9, 500));
    private final PurchaseItemCatalog catalog = id -> java.util.Optional.ofNullable(items.get(id));
    private final BuildPathResolver resolver = new BuildPathResolver(catalog);

    @Test
    void 예산으로_살_수_있는_단일_구매와_묶음을_함께_만든다() {
        var bundles = resolver.resolve(4, ItemInventory.empty(), 1300);
        assertThat(bundles).extracting(BuildPathResolver.PurchaseBundle::itemQuantities)
                .contains(Map.of(1, 1, 2, 1), Map.of(3, 1), Map.of(1, 2))
                .doesNotContain(Map.of(1, 3), Map.of(3, 1, 1, 1), Map.of(4, 1));
        assertThat(find(bundles, Map.of(1, 1, 2, 1)).additionalCost()).isEqualTo(1225);
        assertThat(bundles.stream().map(BuildPathResolver.PurchaseBundle::itemQuantities).distinct().count())
                .isEqualTo(bundles.size());
    }

    @Test
    void 보유_재료의_수량을_한번만_차감한다() {
        var inventory = ItemInventory.empty().add(1);
        var bundles = resolver.resolve(4, inventory, 3000);
        assertThat(find(bundles, Map.of(3, 1)).additionalCost()).isEqualTo(650);
        assertThat(find(bundles, Map.of(4, 1)).additionalCost()).isEqualTo(2650);
        assertThat(find(bundles, Map.of(1, 1)).additionalCost()).isEqualTo(350);
        assertThat(bundles).extracting(BuildPathResolver.PurchaseBundle::itemQuantities).doesNotContain(Map.of(1, 2));
        assertThat(inventory).isEqualTo(ItemInventory.empty().add(1));
    }

    @Test
    void 보유한_상위_재료와_하위_재료를_같은_가지에서_이중_차감하지_않는다() {
        var inventory = ItemInventory.empty().add(3).add(1);
        var bundle = find(resolver.resolve(4, inventory, 3000), Map.of(4, 1));
        assertThat(bundle.additionalCost()).isEqualTo(2000);
        assertThat(bundle.inventoryAfter()).isEqualTo(ItemInventory.empty().add(4).add(1));
    }

    @Test
    void 이미_목표_Core가_있으면_추가_재료를_추천하지_않는다() {
        assertThat(resolver.resolve(4, ItemInventory.empty().add(4), 5000)).isEmpty();
    }

    @Test
    void 예산과_비용이_같으면_포함하고_한골드_부족하면_제외한다() {
        assertThat(resolver.resolve(4, ItemInventory.empty(), 349)).isEmpty();
        assertThat(resolver.resolve(4, ItemInventory.empty(), 350))
                .extracting(BuildPathResolver.PurchaseBundle::itemQuantities).containsExactly(Map.of(1, 1));
    }

    @Test
    void 꽉_찬_인벤토리에서도_재료가_소모되는_조합은_가능하다() {
        var inventory = ItemInventory.empty().add(1).add(1).add(9).add(9).add(9).add(9);
        var bundles = resolver.resolve(4, inventory, 1000);
        assertThat(bundles).extracting(BuildPathResolver.PurchaseBundle::itemQuantities)
                .contains(Map.of(3, 1)).doesNotContain(Map.of(2, 1));
        assertThat(find(bundles, Map.of(3, 1)).inventoryAfter())
                .isEqualTo(ItemInventory.empty().add(3).add(9).add(9).add(9).add(9));
    }

    @Test
    void 슬롯을_먼저_비우는_구매_순서도_찾는다() {
        var inventory = ItemInventory.empty().add(1).add(1).add(9).add(9).add(9).add(9);
        var bundle = find(resolver.resolve(4, inventory, 1300), Map.of(3, 1, 2, 1));
        assertThat(bundle.purchaseOrder()).containsExactly(3, 2);
        assertThat(bundle.additionalCost()).isEqualTo(1175);
    }

    @Test
    void 중간품을_두개_필요로_하는_경로에서_보유_재료를_공유해서_사용하지_않는다() {
        var repeated = new java.util.HashMap<>(items);
        repeated.put(5, item(5, 2500, 3, 3));
        var resolver = new BuildPathResolver(id -> java.util.Optional.ofNullable(repeated.get(id)));
        var bundle = find(resolver.resolve(5, ItemInventory.empty().add(1), 2000), Map.of(3, 2));
        assertThat(bundle.additionalCost()).isEqualTo(1650);
        assertThat(bundle.inventoryAfter()).isEqualTo(ItemInventory.empty().add(3).add(3));
    }

    @Test
    void 변형된_동등_재료도_기존_레시피_대조_규칙으로_차감한다() {
        var variants = new java.util.HashMap<>(items);
        variants.put(3, new PurchaseCatalogItem(3, 1000, true, List.of(1, 1), List.of(4)));
        variants.put(6, new PurchaseCatalogItem(6, 1000, true, List.of(1, 1), List.of(4)));
        var resolver = new BuildPathResolver(id -> java.util.Optional.ofNullable(variants.get(id)));
        var bundle = find(resolver.resolve(4, ItemInventory.empty().add(6), 3000), Map.of(4, 1));
        assertThat(bundle.additionalCost()).isEqualTo(2000);
        assertThat(bundle.inventoryAfter()).isEqualTo(ItemInventory.empty().add(4));
    }

    @Test
    void 카탈로그_누락과_순환은_빈_추천으로_숨기지_않는다() {
        var broken = new java.util.HashMap<>(items);
        broken.put(3, item(3, 1000, 4));
        var cyclic = new BuildPathResolver(id -> java.util.Optional.ofNullable(broken.get(id)));
        assertThatThrownBy(() -> cyclic.resolve(4, ItemInventory.empty(), 5000)).isInstanceOf(IllegalStateException.class);
        var missing = new BuildPathResolver(id -> id == 1 ? java.util.Optional.empty() : catalog.find(id));
        assertThatThrownBy(() -> missing.resolve(4, ItemInventory.empty(), 5000)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 장신구는_별도_슬롯이고_중첩_아이템은_카탈로그_수량을_사용한다() throws Exception {
        String raw = """
                {
                 "1":{"gold":{"total":350,"purchasable":true}},
                 "2":{"gold":{"total":875,"purchasable":true}},
                 "3":{"stacks":0,"from":["1","1"],"gold":{"total":1000,"purchasable":true}},
                 "4":{"from":["3","2"],"gold":{"total":3000,"purchasable":true}},
                 "8":{"gold":{"total":500,"purchasable":true}},
                 "9":{"tags":["Consumable"],"stacks":5,"gold":{"total":50,"purchasable":true}},
                 "10":{"tags":["Trinket"],"gold":{"total":0,"purchasable":false}}
                }
                """;
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        Map<String, dfgg.infrastructure.external.dto.ItemData> data = mapper.readValue(raw,
                new com.fasterxml.jackson.core.type.TypeReference<>() { });
        var catalog = new dfgg.infrastructure.external.catalog.DataDragonPurchaseItemCatalog(data);
        var inventory = ItemInventory.empty().add(10).add(8).add(8).add(8).add(8)
                .add(9).add(9).add(9).add(9).add(9);
        assertThat(catalog.occupiedSlots(inventory)).isEqualTo(5);
        var bundles = new BuildPathResolver(catalog).resolve(4, inventory, 1000);
        assertThat(bundles).extracting(BuildPathResolver.PurchaseBundle::itemQuantities)
                .contains(Map.of(1, 1), Map.of(3, 1)).doesNotContain(Map.of(1, 2));
    }

    @Test
    void 비구매_경로와_다른_아이템은_구매_후보에_넣지_않는다() {
        var changed = new java.util.HashMap<>(items);
        changed.put(3, new PurchaseCatalogItem(3, 1000, false, List.of(1, 1), List.of()));
        var resolver = new BuildPathResolver(id -> java.util.Optional.ofNullable(changed.get(id)));
        assertThat(resolver.resolve(4, ItemInventory.empty(), 1500))
                .extracting(BuildPathResolver.PurchaseBundle::itemQuantities)
                .contains(Map.of(1, 2)).allSatisfy(quantities -> assertThat(quantities).doesNotContainKeys(3, 9));
    }

    private BuildPathResolver.PurchaseBundle find(List<BuildPathResolver.PurchaseBundle> bundles,
                                                  Map<Integer, Integer> quantities) {
        return bundles.stream().filter(bundle -> bundle.itemQuantities().equals(quantities)).findFirst().orElseThrow();
    }

    private static PurchaseCatalogItem item(int id, int price, Integer... ingredients) {
        return new PurchaseCatalogItem(id, price, true, List.of(ingredients), List.of());
    }
}
