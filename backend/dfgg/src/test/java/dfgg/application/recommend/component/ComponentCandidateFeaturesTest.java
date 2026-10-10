package dfgg.application.recommend.component;

import static dfgg.application.recommend.context.TestCompositions.NO_TAGS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.domain.match.BuildPathResolver;
import dfgg.domain.match.ItemInventory;
import dfgg.infrastructure.external.catalog.DataDragonPurchaseItemCatalog;
import dfgg.infrastructure.external.dto.ItemResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.CsvSource;

class ComponentCandidateFeaturesTest {
    @Test
    void retained_items_do_not_leave_floating_point_stat_changes() throws Exception {
        var response = new ObjectMapper().readValue("""
                {"data":{
                  "1":{"gold":{"total":10,"purchasable":true},"stats":{"PercentAttackSpeedMod":0.1}},
                  "2":{"gold":{"total":10,"purchasable":true},"stats":{"PercentAttackSpeedMod":0.2}},
                  "3":{"gold":{"total":10,"purchasable":true},"stats":{"PercentAttackSpeedMod":0.3}},
                  "3071":{"from":["1036"],"gold":{"total":3000,"purchasable":true}},
                  "1036":{"gold":{"total":350,"purchasable":true},"stats":{"FlatPhysicalDamageMod":10}}}}
                """, ItemResponse.class);
        var before = ItemInventory.empty().add(1).add(1).add(2).add(3);
        var candidate = new BuildPathResolver.PurchaseBundle(Map.of(1036, 1), 350, before.add(1036), List.of(1036));
        var features = ComponentCandidateFeatures.calculate(before, candidate, 3071,
                new DataDragonPurchaseItemCatalog(response.data()), response, 120_000, "TOP", NO_TAGS).features();
        assertThat(features.get(16)).isEqualTo(0.0); // 공격력 구매가 공격속도를 바꿨다고 학습시키지 않는다.
        assertThat(features.get(10)).isEqualTo(10.0);
        assertThat(features).hasSize(59);
        assertThat(features.get(29)).isEqualTo(0.0); // 구매할 롱소드의 공격력은 이전 보유량에 포함하지 않는다.
        assertThat(features.get(35)).isCloseTo(0.7, org.assertj.core.data.Offset.offset(1e-12));
    }

    @Test
    void bot_quest_slot_is_shared_by_candidates_and_features_without_removing_boot_stats() throws Exception {
        var response = new ObjectMapper().readValue("""
                {"data":{
                  "3006":{"tags":["Boots"],"gold":{"total":1000,"purchasable":true},"stats":{"PercentAttackSpeedMod":0.25}},
                  "9":{"gold":{"total":100,"purchasable":true}},
                  "1038":{"gold":{"total":1300,"purchasable":true},"stats":{"FlatPhysicalDamageMod":40}},
                  "3031":{"from":["1038"],"gold":{"total":3000,"purchasable":true}}}}
                """, ItemResponse.class);
        var catalog = new DataDragonPurchaseItemCatalog(response.data());
        var before = ItemInventory.empty().add(3006).add(9).add(9).add(9).add(9).add(9);
        var resolver = new BuildPathResolver(catalog);
        assertThat(catalog.occupiedSlots(before)).isEqualTo(6);
        assertThat(resolver.resolve(3031, before, 1300)).isEmpty();
        var questBefore = before.moveBootsToQuestSlot();
        var candidate = resolver.resolve(3031, questBefore, 1300).getFirst();
        assertThat(candidate.itemQuantities()).isEqualTo(Map.of(1038, 1));
        assertThat(candidate.inventoryAfter().quantityOf(3006)).isEqualTo(1);
        assertThat(candidate.inventoryAfter().bootsInQuestSlot()).isTrue();
        var features = ComponentCandidateFeatures.calculate(questBefore, candidate, 3031,
                catalog, response, 120_000, "BOTTOM", NO_TAGS).features();
        assertThat(features.get(3)).isEqualTo(5.0);
        assertThat(features.get(4)).isEqualTo(6.0);
        assertThat(features.get(35)).isEqualTo(0.25); // 별도 슬롯의 신발 능력치도 보유 합계에 남는다.
        assertThat(features.get(16)).isEqualTo(0.0);
        assertThat(questBefore.remove(3006).orElseThrow().add(3006).bootsInQuestSlot()).isTrue();
        assertThat(questBefore).isNotEqualTo(before);
    }

    @Test
    void support_control_wards_keep_quantities_but_do_not_block_component_candidates() throws Exception {
        var response = new ObjectMapper().readValue("""
                {"data":{
                  "9":{"gold":{"total":100,"purchasable":true}},
                  "2055":{"stacks":2,"gold":{"total":75,"purchasable":true}},
                  "1038":{"gold":{"total":1300,"purchasable":true},"stats":{"FlatPhysicalDamageMod":40}},
                  "3031":{"from":["1038"],"gold":{"total":3000,"purchasable":true}}}}
                """, ItemResponse.class);
        var catalog = new DataDragonPurchaseItemCatalog(response.data());
        var before = ItemInventory.empty().add(9).add(9).add(9).add(9).add(9).add(2055).add(2055);
        var resolver = new BuildPathResolver(catalog);
        assertThat(catalog.occupiedSlots(before)).isEqualTo(6);
        assertThat(resolver.resolve(3031, before, 1300)).isEmpty();
        var supportBefore = before.moveControlWardsToQuestSlot();
        var candidate = resolver.resolve(3031, supportBefore, 1300).getFirst();
        assertThat(candidate.inventoryAfter().quantityOf(2055)).isEqualTo(2);
        assertThat(candidate.inventoryAfter().controlWardsInQuestSlot()).isTrue();
        var vector = ComponentCandidateFeatures.calculate(supportBefore, candidate, 3031,
                catalog, response, 120_000, "UTILITY", NO_TAGS).features();
        assertThat(vector.subList(3, 5)).containsExactly(5.0, 6.0);
        assertThat(catalog.occupiedSlots(supportBefore.add(2055))).isEqualTo(6); // 전용 슬롯은 2개까지.
        assertThat(supportBefore.remove(2055).orElseThrow().controlWardsInQuestSlot()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"MIDDLE,3", "MID,3", "UTILITY,5", "SUPPORT,5", "TOP,1", "JUNGLE,2", "BOTTOM,4", "UNKNOWN,6"})
    void purchase_context_uses_minutes_and_existing_position_normalization(String position, int hotIndex) throws Exception {
        var response = new ObjectMapper().readValue("""
                {"version":"16.18.1","data":{"3071":{"from":["1036"],"gold":{"total":3000,"purchasable":true}},
                  "1036":{"gold":{"total":350,"purchasable":true}}}}
                """, ItemResponse.class);
        var candidate = new BuildPathResolver.PurchaseBundle(Map.of(1036, 1), 350,
                ItemInventory.empty().add(1036), List.of(1036));
        var catalog = new DataDragonPurchaseItemCatalog(response.data());
        var features = ComponentCandidateFeatures.calculate(ItemInventory.empty(), candidate, 3071,
                catalog, response, 120_000, position, NO_TAGS).features();
        assertThat(features.get(22)).isEqualTo(2.0);
        assertThat(features.get(22 + hotIndex)).isEqualTo(1.0);
        assertThat(features.subList(23, 29).stream().mapToDouble(Double::doubleValue).sum()).isEqualTo(1.0);
        assertThatThrownBy(() -> ComponentCandidateFeatures.calculate(ItemInventory.empty(), candidate, 3071,
                catalog, response, -1, position, NO_TAGS)).isInstanceOf(IllegalArgumentException.class);
    }
}
