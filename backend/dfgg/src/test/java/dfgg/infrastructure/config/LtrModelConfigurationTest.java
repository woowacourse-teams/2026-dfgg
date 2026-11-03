package dfgg.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dfgg.application.recommend.v3.feature.FeatureName;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
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

    @Test
    @DisplayName("LTR_MODEL_PATH를 주면 그 경로를 Core 모델 경로로 쓴다")
    void modelPath_WhenLtrModelPathSet_UseIt() {
        // given
        String modelPath = directory.resolve("core-model.json").toUri().toString();

        // when & then
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("LTR_MODEL_PATH=" + modelPath)
                .run(context -> assertThat(context.getEnvironment().getProperty("recommendation.ltr.model-path"))
                        .isEqualTo(modelPath));
    }

    @Test
    @DisplayName("LTR_MODEL_PATH가 없으면 저장소에 포함된 classpath 모델을 쓴다")
    void modelPath_WhenLtrModelPathMissing_UseClasspathModel() {
        // when & then
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .run(context -> assertThat(context.getEnvironment().getProperty("recommendation.ltr.model-path"))
                        .isEqualTo("ltr/model.json"));
    }

    @Test
    @DisplayName("지정한 모델 파일이 없으면 기동을 멈추고 경로를 알려준다")
    void ltrModel_WhenFileMissing_ThrowNamingThePath() {
        // given
        String modelPath = directory.resolve("missing.json").toUri().toString();

        // when & then
        assertThatThrownBy(() -> new LtrModelConfiguration().ltrModel(modelPath, new DefaultResourceLoader()))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining(modelPath);
    }
}
