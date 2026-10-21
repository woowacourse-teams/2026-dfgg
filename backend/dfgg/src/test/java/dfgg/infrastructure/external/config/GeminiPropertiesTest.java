package dfgg.infrastructure.external.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GeminiPropertiesTest {

    @Test
    @DisplayName("문자열로 바꿀 때 API 키를 드러내지 않는다")
    void toString_WhenApiKeyPresent_HideApiKey() {
        // given
        GeminiProperties properties = new GeminiProperties(
                "secret-key", "test-model", URI.create("https://aiplatform.googleapis.com"),
                Duration.ofSeconds(5), "minimal");

        // when
        String text = properties.toString();

        // then
        assertThat(text).doesNotContain("secret-key");
    }
}
