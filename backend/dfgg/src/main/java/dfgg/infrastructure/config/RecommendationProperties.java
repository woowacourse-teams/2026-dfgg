package dfgg.infrastructure.config;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("recommendation")
public record RecommendationProperties (

    /** v2가 최신 패치 통계를 사용하기 위해 필요한 최소 게임 표본 수다. */
    @DefaultValue("30")
    int v2MinSampleCount,

    @DefaultValue({"PLATINUM", "EMERALD", "DIAMOND", "MASTER", "GRANDMASTER", "CHALLENGER"})
    List<String> v2Tiers
) {

    private static final Set<String> SUPPORTED_TIERS = Set.of(
            "IRON",
            "BRONZE",
            "SILVER",
            "GOLD",
            "PLATINUM",
            "EMERALD",
            "DIAMOND",
            "MASTER",
            "GRANDMASTER",
            "CHALLENGER"
    );

    public RecommendationProperties {
        if (v2Tiers == null || v2Tiers.isEmpty()) {
            throw new IllegalArgumentException("recommendation.v2-tiers must not be empty");
        }
        v2Tiers = v2Tiers.stream()
                .map(tier -> tier == null ? "" : tier.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
        if (v2Tiers.stream().anyMatch(tier -> !SUPPORTED_TIERS.contains(tier))) {
            throw new IllegalArgumentException("recommendation.v2-tiers contains an unsupported tier");
        }
    }
}
