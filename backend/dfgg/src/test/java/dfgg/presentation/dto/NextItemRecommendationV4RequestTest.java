package dfgg.presentation.dto;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.presentation.dto.request.NextItemRecommendationV4Request;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class NextItemRecommendationV4RequestTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();
    private final JsonMapper mapper = JsonMapper.builder().build();

    @AfterAll
    static void closeValidator() {
        FACTORY.close();
    }

    @Test
    void accepts_empty_inventory_and_zero_gold_and_time() {
        assertThat(violations(request(body()))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"myChampion", "purchasedItemIds", "allies", "enemies", "inventoryItems", "availableGold", "gameTimeMs"})
    void rejects_missing_or_null_required_fields(String field) {
        ObjectNode missing = body();
        missing.remove(field);
        assertThat(violations(request(missing))).anyMatch(path -> path.startsWith(field));

        ObjectNode explicitNull = body();
        explicitNull.putNull(field);
        assertThat(violations(request(explicitNull))).anyMatch(path -> path.startsWith(field));
    }

    @ParameterizedTest
    @ValueSource(strings = {"availableGold", "gameTimeMs"})
    void rejects_negative_current_state(String field) {
        ObjectNode body = body();
        body.put(field, -1);
        assertThat(violations(request(body))).contains(field);
    }

    @ParameterizedTest
    @CsvSource({"itemId,0", "itemId,-1", "itemId,2147483648", "quantity,0", "quantity,-1"})
    void rejects_invalid_inventory_ids_and_quantities(String field, long value) {
        ObjectNode body = body();
        ObjectNode item = body.putArray("inventoryItems").addObject();
        item.put("itemId", 1036).put("quantity", 1).put(field, value);
        assertThat(violations(request(body))).anyMatch(path -> path.startsWith("inventoryItems") && path.endsWith(field));
    }

    @ParameterizedTest
    @ValueSource(strings = {"itemId", "quantity"})
    void rejects_missing_inventory_item_fields(String field) {
        ObjectNode body = body();
        ObjectNode item = body.putArray("inventoryItems").addObject();
        item.put("itemId", 1036).put("quantity", 1).remove(field);
        assertThat(violations(request(body))).anyMatch(path -> path.startsWith("inventoryItems") && path.endsWith(field));
    }

    @ParameterizedTest
    @ValueSource(strings = {"purchasedItemIds", "inventoryItems", "allies", "enemies"})
    void rejects_null_list_elements(String field) {
        ObjectNode body = body();
        body.withArray(field).addNull();
        assertThat(violations(request(body))).anyMatch(path -> path.startsWith(field));
    }

    @Test
    void inventory_quantity_is_separate_from_core_purchase_history() {
        ObjectNode body = body();
        body.withArray("purchasedItemIds").add(3071).add(6333);
        body.withArray("inventoryItems").addObject().put("itemId", 1036).put("quantity", 2);
        var request = request(body);

        assertThat(violations(request)).isEmpty();
        assertThat(request.toCoreRequest().purchasedItemIds()).containsExactly(3071L, 6333L);
        assertThat(request.inventoryItems().getFirst().quantity()).isEqualTo(2);
        assertThat(request.toCoreRequest().patch()).isNull();
    }

    @Test
    void duplicate_inventory_ids_must_be_expressed_as_quantity() {
        ObjectNode body = body();
        body.withArray("inventoryItems").addObject().put("itemId", 1036).put("quantity", 1);
        body.withArray("inventoryItems").addObject().put("itemId", 1036).put("quantity", 1);
        assertThat(violations(request(body))).contains("inventoryItemIdsUnique");
    }

    @ParameterizedTest
    @CsvSource({"TOP,6,true", "TOP,7,false", "BOTTOM,7,true", "BOTTOM,8,false"})
    void preserves_position_specific_core_history_limit(String position, int count, boolean valid) {
        ObjectNode body = body();
        ((ObjectNode) body.get("myChampion")).put("position", position);
        for (int i = 0; i < count; i++) {
            body.withArray("purchasedItemIds").add(3000 + i);
        }
        assertThat(violations(request(body)).isEmpty()).isEqualTo(valid);
    }

    @Test
    void inventory_array_length_does_not_define_occupied_slots() {
        ObjectNode body = body();
        for (int id = 1001; id <= 1007; id++) {
            body.withArray("inventoryItems").addObject().put("itemId", id).put("quantity", 1);
        }
        assertThat(violations(request(body))).isEmpty();
    }

    @Test
    void validates_nested_champion_and_team_size() {
        ObjectNode body = body();
        ((ObjectNode) body.get("allies").get(0)).put("name", "").put("position", "INVALID");
        body.withArray("enemies").remove(0);
        assertThat(violations(request(body)))
                .contains("allies[0].name", "allies[0].position", "enemies");
    }

    @Test
    void helper_methods_are_not_part_of_json_contract() {
        var json = mapper.valueToTree(request(body()));
        assertThat(json.has("purchasedItemCountWithinLimit")).isFalse();
        assertThat(json.has("inventoryItemIdsUnique")).isFalse();
        assertThat(json.has("coreRequest")).isFalse();
        assertThat(json.has("bootsInQuestSlot")).isFalse();
    }

    private Set<String> violations(NextItemRecommendationV4Request request) {
        return VALIDATOR.validate(request).stream().map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    private NextItemRecommendationV4Request request(ObjectNode json) {
        return mapper.treeToValue(json, NextItemRecommendationV4Request.class);
    }

    private ObjectNode body() {
        return (ObjectNode) mapper.readTree("""
                {
                  "myChampion": {"name":"Garen", "position":"TOP"},
                  "purchasedItemIds": [],
                  "allies": [
                    {"name":"Ahri", "position":"MID"}, {"name":"LeeSin", "position":"JUNGLE"},
                    {"name":"Jinx", "position":"BOTTOM"}, {"name":"Thresh", "position":"SUPPORT"}
                  ],
                  "enemies": [
                    {"name":"Ornn", "position":"TOP"}, {"name":"Vi", "position":"JUNGLE"},
                    {"name":"Annie", "position":"MID"}, {"name":"Ashe", "position":"BOTTOM"},
                    {"name":"Braum", "position":"SUPPORT"}
                  ],
                  "inventoryItems": [], "availableGold": 0, "gameTimeMs": 0
                }
                """);
    }
}
