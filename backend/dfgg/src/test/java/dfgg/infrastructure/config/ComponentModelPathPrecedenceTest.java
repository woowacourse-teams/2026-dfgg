package dfgg.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

class ComponentModelPathPrecedenceTest {
    private static final String ENV_FILE_PATH = "file:/opt/dfgg/models/component-ltr-16.19-261020/model.json";
    private static final String CONTAINER_PATH = "file:/models/component/model.json";

    @TempDir Path directory;
    private Path envFile;

    @BeforeEach
    void setUp() throws IOException {
        envFile = directory.resolve(".env");
        Files.writeString(envFile, "COMPONENT_LTR_MODEL_PATH=" + ENV_FILE_PATH + "\n");
    }

    @Test
    @DisplayName("컨테이너 환경 변수가 있으면 .env의 같은 키보다 우선한다")
    void modelPath_WhenContainerEnvironmentSet_OverrideEnvFile() {
        // given
        Map<String, Object> environment = new HashMap<>(importEnvFile());
        environment.put("COMPONENT_LTR_MODEL_PATH", CONTAINER_PATH);

        // when & then
        runnerWith(environment).run(context -> assertThat(
                context.getEnvironment().getProperty("recommendation.component-ltr.model-path"))
                .isEqualTo(CONTAINER_PATH));
    }

    @Test
    @DisplayName("컨테이너 환경 변수가 없으면 .env의 값을 쓴다")
    void modelPath_WhenContainerEnvironmentMissing_UseEnvFile() {
        // when & then
        runnerWith(importEnvFile()).run(context -> assertThat(
                context.getEnvironment().getProperty("recommendation.component-ltr.model-path"))
                .isEqualTo(ENV_FILE_PATH));
    }

    private Map<String, Object> importEnvFile() {
        return Map.of("SPRING_CONFIG_IMPORT", "optional:file:" + envFile + "[.properties]");
    }

    // 실제 OS 환경 변수 자리(systemEnvironment)를 바꿔 끼워 컨테이너의 Environment=를 흉내 낸다.
    private ApplicationContextRunner runnerWith(Map<String, Object> environment) {
        return new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().getPropertySources().replace(
                        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        new SystemEnvironmentPropertySource(
                                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, environment)))
                .withInitializer(new ConfigDataApplicationContextInitializer());
    }
}
