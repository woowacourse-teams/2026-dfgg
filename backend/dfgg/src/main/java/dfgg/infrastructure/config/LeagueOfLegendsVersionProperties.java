package dfgg.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 기동할 때의 서비스 패치(예: {@code 16.18}). 실행 중에는 {@code PatchMonitor}가 새 패치를 감지해
 * {@code ServicePatch}를 앞으로만 바꾸므로, 이 값은 시작값이자 v4 Component 카탈로그의 기준 패치다.
 */
@ConfigurationProperties("riot")
public record LeagueOfLegendsVersionProperties(
        String version
) {
    public LeagueOfLegendsVersionProperties {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("riot.version must not be blank");
        }
        version = version.strip();
    }
}
