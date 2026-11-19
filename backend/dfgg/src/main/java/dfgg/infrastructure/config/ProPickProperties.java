package dfgg.infrastructure.config;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "pro-picks")
public record ProPickProperties(
        @NotEmpty List<String> leagues,
        @Positive int lookbackDays
) {
}
