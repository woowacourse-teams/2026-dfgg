package dfgg.infrastructure.external.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Leaguepedia 봇 계정 설정. 봇 이름·비밀번호는 비어 있어도 기동되고, 호출할 때만 막는다.
 * 요청 간격 12초는 IP당 분당 5회 제한(2026-10-22 실측)에 맞춘 값이다.
 */
@Validated
@ConfigurationProperties(prefix = "leaguepedia")
public record LeaguepediaProperties(
        @NotNull URI baseUrl,
        String botUsername,
        String botPassword,
        @NotBlank String userAgent,
        @NotNull Duration requestInterval,
        @NotNull Duration retryBackoff,
        @PositiveOrZero int maxRetries
) {

    public boolean hasCredentials() {
        return isPresent(botUsername) && isPresent(botPassword);
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    @Override
    public String toString() {
        return "LeaguepediaProperties[baseUrl=" + baseUrl
                + ", botUsername=" + botUsername
                + ", userAgent=" + userAgent
                + ", requestInterval=" + requestInterval
                + ", retryBackoff=" + retryBackoff
                + ", maxRetries=" + maxRetries + "]";
    }
}
