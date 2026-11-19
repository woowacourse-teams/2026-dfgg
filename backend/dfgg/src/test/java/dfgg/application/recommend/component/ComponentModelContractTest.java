package dfgg.application.recommend.component;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.application.recommend.v3.ranker.LightGbmModelLoader;
import dfgg.infrastructure.config.ComponentLtrConfiguration;
import java.io.ByteArrayInputStream;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ComponentModelContractTest {
    @Test
    void model_and_lookup_must_have_the_same_hash_including_disabled_lookup() throws Exception {
        var config = new ComponentLtrConfiguration();
        var lookup = new dfgg.domain.match.ComponentPolicyLookup("16.18", "a".repeat(64), Map.of());
        for (String model : java.util.List.of("{}", "{\"policy_lookup_sha256\":\"" + "b".repeat(64) + "\"}")) {
            var resource = new org.springframework.core.io.ByteArrayResource(model.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            assertThatThrownBy(() -> config.componentLtrModel(resource, lookup))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("SHA-256");
        }
        var resource = new org.springframework.core.io.ByteArrayResource(
                ("{\"policy_lookup_sha256\":\"" + "a".repeat(64) + "\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThatThrownBy(() -> config.componentLtrModel(resource, dfgg.domain.match.ComponentPolicyLookup.disabled()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("SHA-256");
    }

    @Test
    void old_46_feature_model_is_rejected_even_when_it_has_valid_trees() throws Exception {
        var model = Map.of("objective", "lambdarank", "schema_fingerprint", "54d9de4694c4f106003206c2489c3ba8cdd0346aad3c8c375fed32a4682e9188",
                "feature_names", ComponentCandidateFeatures.FEATURE_NAMES.subList(0, 46), "trees", java.util.List.of(
                        Map.of("split_feature", java.util.List.of(), "threshold", java.util.List.of(), "default_left", java.util.List.of(),
                               "left", java.util.List.of(), "right", java.util.List.of(), "leaf_value", java.util.List.of(1.0),
                               "node_cover", java.util.List.of(), "leaf_cover", java.util.List.of(1.0))));
        var bytes = new ObjectMapper().writeValueAsBytes(model);
        assertThatThrownBy(() -> LightGbmModelLoader.load(new ByteArrayInputStream(bytes), ComponentCandidateFeatures.FEATURE_NAMES,
                ComponentLtrConfiguration.SCHEMA_FINGERPRINT)).isInstanceOf(IllegalStateException.class);
    }
}
