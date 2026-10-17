package dfgg.infrastructure.recommend;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.domain.match.SituationalThresholds;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SituationalComponentLookupLoaderTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Map<String, Object> LIFT = Map.of("itemId", 3123, "enemyChampionId", 19, "role", "BOTTOM", "support", 47, "lift", 3.4);

    static Map<String, Object> lookup() {
        return new HashMap<>(Map.of("schemaVersion", "situational-component-lookup-v1", "patch", "16.18", "miningSplit", "all",
                "whitelist", Map.of("3916", "GRIEVOUS_WOUNDS", "3123", "GRIEVOUS_WOUNDS", "3076", "GRIEVOUS_WOUNDS", "1033", "MAGIC_RESIST"),
                "minSupport", 30, "minGames", 50, "minLift", 1.5, "minBaseRate", .01,
                "lifts", List.of(LIFT),
                "baseRates", List.of(Map.of("championId", 222, "role", "BOTTOM", "itemId", 3123, "games", 300, "baseRate", .12))));
    }

    @Test
    void loads_lifts_and_base_rates() throws Exception {
        var loaded = SituationalComponentLookupLoader.load(MAPPER.writeValueAsBytes(lookup()));
        assertThat(loaded.evidenceFor(222, "BOTTOM", List.of(19), new SituationalThresholds(2.0, .01)))
                .singleElement().satisfies(e -> assertThat(e.reasonChampionIds()).containsExactly(19));
    }

    @Test
    void rejects_contract_whitelist_floor_and_duplicate_violations() throws Exception {
        var whitelist = lookup();
        whitelist.put("whitelist", Map.of("3916", "GRIEVOUS_WOUNDS"));
        var floor = lookup();
        floor.put("minLift", 2.0);
        var schema = lookup();
        schema.put("schemaVersion", "component-policy-lookup-v1");
        var split = lookup();
        split.put("miningSplit", "validation");
        var duplicate = lookup();
        duplicate.put("lifts", List.of(LIFT, LIFT));
        for (var invalid : List.of(whitelist, floor, schema, split, duplicate)) {
            assertThatThrownBy(() -> SituationalComponentLookupLoader.load(MAPPER.writeValueAsBytes(invalid)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
