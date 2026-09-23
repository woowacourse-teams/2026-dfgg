package dfgg.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 서비스가 기준으로 삼는 리그 오브 레전드 패치 버전(예: {@code 16.18}). 응답 이미지 URL의 버전 폴더다.
 * <p>
 * 설정은 기동할 때만 읽는다. 새 패치 이미지를 S3에 올린 뒤 {@code LEAGUE_OF_LEGENDS_VERSION}을 바꾸고 재배포한다.
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
