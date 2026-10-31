package dfgg.infrastructure.config;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.application.recommend.situational.SituationalComponentRuntime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

class SituationalComponentConfigurationTest {
    @TempDir Path directory;
    private final SituationalComponentConfiguration configuration = new SituationalComponentConfiguration();

    private String lookupPath(String patch) throws Exception {
        var lookup = new HashMap<String, Object>(Map.of("schemaVersion", "situational-component-lookup-v1", "patch", patch,
                "miningSplit", "all", "whitelist", Map.of("3916", "GRIEVOUS_WOUNDS", "3123", "GRIEVOUS_WOUNDS",
                        "3076", "GRIEVOUS_WOUNDS", "1033", "MAGIC_RESIST"),
                "minSupport", 30, "minGames", 50, "minLift", 1.5, "minBaseRate", .01, "lifts", List.of(), "baseRates", List.of()));
        return Files.write(directory.resolve("lookup-" + patch + ".json"), new ObjectMapper().writeValueAsBytes(lookup)).toUri().toString();
    }

    private SituationalComponentRuntime runtime(String path, double minLift, double minBaseRate) {
        return configuration.situationalComponentRuntime(path, minLift, minBaseRate, new DefaultResourceLoader());
    }

    @Test
    void valid_lookup_and_thresholds_enable_the_runtime() throws Exception {
        assertThat(runtime(lookupPath("16.18"), 2.0, .01).isAvailable()).isTrue();
        assertThat(runtime(lookupPath("16.18"), 3.0, .05).isAvailable()).isTrue();
        assertThat(runtime(lookupPath("16.17"), 2.0, .01).isAvailable()).isTrue();
    }

    @Test
    void missing_path_invalid_file_or_threshold_below_floor_only_disables_situational() throws Exception {
        assertThat(runtime("", 2.0, .01).isAvailable()).isFalse();
        assertThat(runtime("classpath:missing-situational.json", 2.0, .01).isAvailable()).isFalse();
        assertThat(runtime(lookupPath("16.18"), 1.2, .01).isAvailable()).isFalse();
    }
}
