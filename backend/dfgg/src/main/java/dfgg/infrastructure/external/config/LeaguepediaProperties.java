package dfgg.infrastructure.external.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Leaguepedia 봇 계정 설정. 봇 이름·비밀번호는 비어 있어도 기동되고, 호출할 때만 막는다.
 */
@Validated
@ConfigurationProperties(prefix = "leaguepedia")
public record LeaguepediaProperties(
        @NotNull URI baseUrl,
        String botUsername,
        String botPassword,
        @NotBlank String userAgent
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
                + ", userAgent=" + userAgent + "]";
    }
}
