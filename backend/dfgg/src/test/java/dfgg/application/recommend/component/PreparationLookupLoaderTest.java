package dfgg.application.recommend.component;

import static org.assertj.core.api.Assertions.*;
import dfgg.infrastructure.recommend.PreparationLookupLoader;
import org.junit.jupiter.api.Test;

class PreparationLookupLoaderTest {
    private String artifact() {
        return """
            {"schemaVersion":"component-preparation-v2","patch":"16.18","miningSplit":"train",
             "minPatternCount":50,"minFutureRecipeLinkRate":0.70,"maxCandidatesPerKey":3,
             "trainMatchIds":["KR_TRAIN"],"splitManifestSha256":"%s",
             "entries":[{"championId":81,"role":"BOTTOM","targetItemId":3078,
             "items":[{"itemId":3070,"eligibleCount":50,"eligibleQueryCount":200,"observationRate":0.25,"futureRecipeLinkedCount":40,"futureRecipeLinkRate":0.8}]}]}
            """.formatted("a".repeat(64));
    }
    @Test void valid_lookup_has_byte_fingerprint_and_is_immutable() throws Exception {
        var lookup = PreparationLookupLoader.load(artifact().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(lookup.sha256()).hasSize(64);
        assertThat(lookup.find(81, "BOTTOM", 3078)).singleElement().satisfies(p -> assertThat(p.itemId()).isEqualTo(3070));
        assertThatThrownBy(() -> lookup.find(81, "BOTTOM", 3078).clear()).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void heldout_metadata_and_unpromoted_patterns_are_rejected() {
        for (String invalid : java.util.List.of(artifact().replace("component-preparation-v2", "component-preparation-v1"),
                artifact().replace("\"observationRate\":0.25", "\"observationRate\":0.5"),
                artifact().replace("\"eligibleQueryCount\":200", "\"eligibleQueryCount\":49"),artifact().replace("\"train\"", "\"test\""),
                artifact().replace("\"eligibleCount\":50", "\"eligibleCount\":49"),
                artifact().replace("\"futureRecipeLinkRate\":0.8", "\"futureRecipeLinkRate\":0.69"),
                artifact().replace("\"patch\":\"16.18\"", "\"patch\":\"16.18.1\""))) {
            assertThatThrownBy(() -> PreparationLookupLoader.load(invalid.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
