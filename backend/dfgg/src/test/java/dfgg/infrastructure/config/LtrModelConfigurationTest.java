package dfgg.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dfgg.application.recommend.v3.feature.FeatureName;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

class LtrModelConfigurationTest {
    @TempDir Path directory;

    @Test
    void externalFileAndOriginalClasspathUseTheSameContract() throws Exception {
        var resources = new DefaultResourceLoader();
        byte[] bytes;
        try (var input = resources.getResource("classpath:ltr/model.json").getInputStream()) {
            bytes = input.readAllBytes();
        }
        Path file = directory.resolve("core-model.json");
        Files.write(file, bytes);
        var configuration = new LtrModelConfiguration();
        var original = configuration.ltrModel("ltr/model.json", resources);
        var external = configuration.ltrModel(file.toUri().toString(), resources);
        var classpath = configuration.ltrModel("classpath:ltr/model.json", resources);
        double[] features = new double[FeatureName.values().length];
        assertThat(external.predict(features)).isEqualTo(original.predict(features));
        assertThat(classpath.predict(features)).isEqualTo(original.predict(features));
        Files.writeString(file, "{}");
        assertThatThrownBy(() -> configuration.ltrModel(file.toUri().toString(), resources))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("지문");
    }
}
