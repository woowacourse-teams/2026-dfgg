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
    void old_41_feature_model_is_rejected_even_when_it_has_valid_trees() throws Exception {
        var model = Map.of("objective", "lambdarank", "schema_fingerprint", "9d8228aac189ef9ed2238458e922010d74fcda150865f938cbf57730cddc63ec",
                "feature_names", ComponentCandidateFeatures.FEATURE_NAMES.subList(0, 41), "trees", java.util.List.of(
                        Map.of("split_feature", java.util.List.of(), "threshold", java.util.List.of(), "default_left", java.util.List.of(),
                               "left", java.util.List.of(), "right", java.util.List.of(), "leaf_value", java.util.List.of(1.0),
                               "node_cover", java.util.List.of(), "leaf_cover", java.util.List.of(1.0))));
        var bytes = new ObjectMapper().writeValueAsBytes(model);
        assertThatThrownBy(() -> LightGbmModelLoader.load(new ByteArrayInputStream(bytes), ComponentCandidateFeatures.FEATURE_NAMES,
                ComponentLtrConfiguration.SCHEMA_FINGERPRINT)).isInstanceOf(IllegalStateException.class);
    }
}
