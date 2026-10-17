package dfgg.presentation.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dfgg.presentation.dto.response.NextItemRecommendationResponse;
import dfgg.presentation.dto.response.NextItemRecommendationV4Response;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class NextItemRecommendationV4ResponseTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final RecommendedItemDto core = new RecommendedItemDto(3071L, "칠흑의 양날 도끼", "https://example.com/3071.png",
            new RecommendationDescription(List.of(), List.of(), List.of("체력")));
    private final PurchaseItemDto swords = new PurchaseItemDto(1036L, "롱소드", "https://example.com/1036.png", 2);

    @Test
    void only_v4_adds_one_purchase_bundle_to_existing_core_fields() {
        var bundle = new PurchaseBundleDto(List.of(swords), 700, List.of(1036L, 1036L));
        var v3 = mapper.valueToTree(new NextItemRecommendationResponse(List.of(core)));
        var v4 = mapper.valueToTree(new NextItemRecommendationV4Response(List.of(RecommendedItemV4Dto.of(core, bundle))));
        var v3Item = v3.get("recommendedItems").get(0);
        var v4Item = v4.get("recommendedItems").get(0);

        assertThat(v3Item.size()).isEqualTo(4);
        assertThat(v3Item.has("purchaseBundle")).isFalse();
        for (String field : List.of("id", "name", "imageUrl", "description")) {
            assertThat(v4Item.get(field)).isEqualTo(v3Item.get(field));
        }
        assertThat(v4Item.get("purchaseBundle").isObject()).isTrue();
        assertThat(v4Item.get("purchaseBundle").get("items").get(0).get("quantity").asInt()).isEqualTo(2);
        assertThat(v4Item.get("purchaseBundle").get("purchaseOrder").size()).isEqualTo(2);
        assertThat(v4Item.has("status")).isFalse();
    }

    @Test
    void missing_bundle_keeps_core_with_explicit_null_and_no_failure_details() {
        var response = new NextItemRecommendationV4Response(List.of(RecommendedItemV4Dto.of(core, null)));
        var item = mapper.valueToTree(response).get("recommendedItems").get(0);
        assertThat(item.get("id").asLong()).isEqualTo(3071L);
        assertThat(item.has("purchaseBundle")).isTrue();
        assertThat(item.get("purchaseBundle").isNull()).isTrue();
        assertThat(item.size()).isEqualTo(5);
    }

    @Test
    void rejects_order_that_does_not_execute_the_entire_bundle() {
        assertThatThrownBy(() -> new PurchaseBundleDto(List.of(swords), 700, List.of(1036L)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PurchaseBundleDto(List.of(swords), 700, List.of(1036L, 1001L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_empty_or_duplicate_items_and_negative_cost() {
        assertThatThrownBy(() -> new PurchaseBundleDto(List.of(), 0, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PurchaseBundleDto(List.of(swords, swords), 1400, List.of(1036L, 1036L, 1036L, 1036L)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PurchaseBundleDto(List.of(swords), -1, List.of(1036L, 1036L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_nonpositive_purchase_quantity_but_accepts_zero_additional_cost() {
        assertThatThrownBy(() -> new PurchaseItemDto(1036L, "롱소드", "image", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new PurchaseBundleDto(List.of(swords), 0, List.of(1036L, 1036L)).additionalCost()).isZero();
    }
}
