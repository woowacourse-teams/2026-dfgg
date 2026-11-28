package dfgg.application.recommend.situational;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.domain.match.ItemInventory;
import dfgg.domain.match.SituationalComponentLookup;
import dfgg.domain.match.SituationalComponentPolicy.Effect;
import dfgg.domain.match.SituationalThresholds;
import dfgg.infrastructure.external.dto.ItemResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SituationalComponentRuntimeTest {
    private static final int GAREN = 86, WARWICK = 19, KENNEN = 85;
    private final ItemResponse catalog;
    private final SituationalComponentRuntime runtime;

    SituationalComponentRuntimeTest() throws Exception {
        catalog = new ObjectMapper().readValue("""
                {"version":"16.18.1","data":{
                  "1042":{"name":"단검","gold":{"total":250,"purchasable":true}},
                  "1029":{"name":"천 갑옷","gold":{"total":300,"purchasable":true}},
                  "1033":{"name":"마법무효화의 망토","gold":{"total":400,"purchasable":true}},
                  "2055":{"name":"제어 와드","gold":{"total":75,"purchasable":true}},
                  "3123":{"name":"처형인의 대검","from":["1042"],"gold":{"total":800,"purchasable":true}},
                  "3076":{"name":"덤불 조끼","from":["1029"],"gold":{"total":800,"purchasable":true}},
                  "3075":{"name":"가시 갑옷","from":["3076"],"gold":{"total":2450,"purchasable":true}},
                  "3916":{"name":"망각의 구","gold":{"total":800,"purchasable":true}}
                }}
                """, ItemResponse.class);
        var lookup = new SituationalComponentLookup("16.18",
                Map.of(new SituationalComponentLookup.LiftKey(3076, WARWICK, "TOP"), new SituationalComponentLookup.Lift(41, 2.2),
                        new SituationalComponentLookup.LiftKey(3123, WARWICK, "TOP"), new SituationalComponentLookup.Lift(47, 3.4),
                        new SituationalComponentLookup.LiftKey(1033, KENNEN, "TOP"), new SituationalComponentLookup.Lift(130, 2.4)),
                Map.of(new SituationalComponentLookup.BaseKey(GAREN, "TOP", 3076), .20,
                        new SituationalComponentLookup.BaseKey(GAREN, "TOP", 3123), .08,
                        new SituationalComponentLookup.BaseKey(GAREN, "TOP", 1033), .15));
        runtime = SituationalComponentRuntime.available(lookup, new SituationalThresholds(2.0, .01));
    }

    private List<SituationalComponentRuntime.SituationalRecommendation> recommend(ItemInventory inventory, List<Integer> purchased, long gold) {
        return runtime.recommend(GAREN, "TOP", List.of(WARWICK, KENNEN), inventory, purchased, gold, catalog);
    }

    @Test void one_item_per_effect_with_reason_cost_and_affordability() {
        var result = recommend(ItemInventory.empty(), List.of(), 500);
        assertThat(result).extracting(SituationalComponentRuntime.SituationalRecommendation::itemId).containsExactly(3123, 1033);
        assertThat(result.getFirst().effect()).isEqualTo(Effect.GRIEVOUS_WOUNDS);
        assertThat(result.getFirst().reasonChampionIds()).containsExactly(WARWICK);
        assertThat(result.getFirst().additionalCost()).isEqualTo(800);
        assertThat(result.getFirst().affordable()).isFalse();
        assertThat(result.getLast().affordable()).isTrue();
    }

    @Test void additional_cost_uses_owned_materials() {
        var withDagger = recommend(ItemInventory.empty().add(1042), List.of(), 600);
        assertThat(withDagger.getFirst().additionalCost()).isEqualTo(550);
        assertThat(withDagger.getFirst().affordable()).isTrue();
    }

    @Test void owned_effect_in_inventory_or_purchased_core_skips_the_whole_effect() {
        assertThat(recommend(ItemInventory.empty().add(3916), List.of(), 1000))
                .extracting(SituationalComponentRuntime.SituationalRecommendation::effect).containsExactly(Effect.MAGIC_RESIST);
        assertThat(recommend(ItemInventory.empty(), List.of(3075), 1000))
                .extracting(SituationalComponentRuntime.SituationalRecommendation::effect).containsExactly(Effect.MAGIC_RESIST);
    }

    @Test void full_inventory_falls_back_or_skips_items_that_cannot_be_bought() {
        var full = ItemInventory.empty().add(1029).add(2055).add(2055).add(2055).add(2055).add(2055);
        // 천 갑옷을 소비하는 덤불 조끼만 슬롯 없이 살 수 있다.
        assertThat(recommend(full, List.of(), 1000)).extracting(SituationalComponentRuntime.SituationalRecommendation::itemId)
                .containsExactly(3076);
    }

    @Test void disabled_runtime_returns_empty() {
        assertThat(SituationalComponentRuntime.disabled().recommend(GAREN, "TOP", List.of(WARWICK), ItemInventory.empty(),
                List.of(), 1000, catalog)).isEmpty();
    }
}
