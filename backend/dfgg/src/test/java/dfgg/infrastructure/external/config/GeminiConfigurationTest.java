package dfgg.infrastructure.external.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class GeminiConfigurationTest {

    private final PropertySource<?> applicationProperties = loadApplicationProperties();

    @Test
    @DisplayName("모델을 지정하지 않으면 gemini-3.5-flash를 쓴다")
    void model_WhenEnvironmentVariableAbsent_DefaultToGemini35Flash() {
        // when
        Object model = applicationProperties.getProperty("gemini.model");

        // then
        assertThat(model)
                .as("비어 있으면 아무 오류 없이 이유가 전부 null이 된다")
                .isEqualTo("${GEMINI_MODEL:gemini-3.5-flash}");
    }

    @Test
    @DisplayName("타임아웃을 지정하지 않으면 8초를 쓴다")
    void timeout_WhenEnvironmentVariableAbsent_DefaultToEightSeconds() {
        // when
        Object timeout = applicationProperties.getProperty("gemini.timeout");

        // then
        assertThat(timeout)
                .as("minimal 실측에서 성공 응답 최대가 5.5초였다. 5초면 정상 응답도 잘린다")
                .isEqualTo("${GEMINI_TIMEOUT:8s}");
    }

    private static PropertySource<?> loadApplicationProperties() {
        try {
            return new YamlPropertySourceLoader()
                    .load("application.yml", new ClassPathResource("application.yml"))
                    .getFirst();
        } catch (IOException exception) {
            throw new IllegalStateException("application.yml을 읽을 수 없습니다", exception);
        }
    }
}
