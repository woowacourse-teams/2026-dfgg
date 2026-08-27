package dfgg.infrastructure.external.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "riot.api")
public record RiotApiProperties(
        @NotBlank String key,
        @NotNull URI platformBaseUrl,
        @NotNull URI regionalBaseUrl,
        @Positive int requestsPerSecond,
        @Positive int requestsPerTwoMinutes
) {

    @Override
    public String toString() {
        return "RiotApiProperties[platformBaseUrl=" + platformBaseUrl
                + ", regionalBaseUrl=" + regionalBaseUrl
                + ", requestsPerSecond=" + requestsPerSecond
                + ", requestsPerTwoMinutes=" + requestsPerTwoMinutes + "]";
    }
}
