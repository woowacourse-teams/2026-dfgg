package dfgg.infrastructure.external.config;

import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 키와 모델은 비어 있어도 기동한다. 비어 있으면 호출 시점에 사용 불가로 처리한다.
 * {@code thinkingLevel}이 비어 있으면 모델 기본값으로 생각한다.
 */
@Validated
@ConfigurationProperties(prefix = "gemini")
public record GeminiProperties(
        String apiKey,
        String model,
        @NotNull URI baseUrl,
        @NotNull Duration timeout,
        String thinkingLevel
) {

    public boolean hasCredentials() {
        return isPresent(apiKey) && isPresent(model);
    }

    public boolean hasThinkingLevel() {
        return isPresent(thinkingLevel);
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    @Override
    public String toString() {
        return "GeminiProperties[model=" + model
                + ", baseUrl=" + baseUrl
                + ", timeout=" + timeout
                + ", thinkingLevel=" + thinkingLevel + "]";
    }
}
