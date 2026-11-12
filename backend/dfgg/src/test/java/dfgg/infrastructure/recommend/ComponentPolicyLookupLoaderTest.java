package dfgg.infrastructure.recommend;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ComponentPolicyLookupLoaderTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Map<String, Object> EZREAL = Map.of("championId", 81, "role", "BOTTOM", "games", 1062, "linePurchaseRate", .85);

    private Map<String, Object> lookup() {
        var prepared = new HashMap<String, Object>(Map.of("whitelist", Map.of("3070", List.of(3003, 3004, 3119)), "minGames", 50,
                "minLinePurchaseRate", .30, "entries", List.of(EZREAL)));
        return new HashMap<>(Map.of("schemaVersion", "component-policy-lookup-v2", "patch", "16.18", "miningSplit", "train",
                "trainMatchIds", List.of("KR_1"), "prepared", prepared));
    }

    @Test
    void loads_entries_and_fingerprints_exact_bytes() throws Exception {
        byte[] bytes = MAPPER.writeValueAsBytes(lookup());
        var loaded = ComponentPolicyLookupLoader.load(bytes);
        assertThat(loaded.sha256()).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        assertThat(loaded.evidenceFor(81, "BOTTOM")).extracting(e -> e.itemId()).containsExactly(3070);
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejects_contract_whitelist_threshold_and_duplicate_violations() throws Exception {
        var changedWhitelist = lookup();
        ((Map<String, Object>) changedWhitelist.get("prepared")).put("whitelist", Map.of("3070", List.of(3004)));
        var changedThreshold = lookup();
        ((Map<String, Object>) changedThreshold.get("prepared")).put("minLinePurchaseRate", .20);
        var validationSplit = lookup();
        validationSplit.put("miningSplit", "all");
        var oldSchema = lookup();
        oldSchema.put("schemaVersion", "component-policy-lookup-v1");
        var duplicate = lookup();
        ((Map<String, Object>) duplicate.get("prepared")).put("entries", List.of(EZREAL, EZREAL));
        for (var invalid : List.of(changedWhitelist, changedThreshold, validationSplit, oldSchema, duplicate)) {
            assertThatThrownBy(() -> ComponentPolicyLookupLoader.load(MAPPER.writeValueAsBytes(invalid)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
