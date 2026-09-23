package dfgg.infrastructure.config;

import dfgg.domain.image.ImageUrls;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 이미지 자산 저장소. 업로드와 응답 URL이 같은 버킷·리전을 본다.
 * <p>
 * 비어 있으면 기동을 막는다 — 틀린 URL을 조용히 내보내면 화면에서만 깨지고 서버는 모른다.
 */
@ConfigurationProperties("assets")
public record AssetProperties(S3 s3) {

    public AssetProperties {
        requireText(s3 == null ? null : s3.bucket(), "assets.s3.bucket");
        requireText(s3.region(), "assets.s3.region");
    }

    /** @param version 이미지를 올린 버전 폴더({@code riot.version}) */
    public ImageUrls imageUrls(String version) {
        return new ImageUrls(s3.bucket(), s3.region(), version);
    }

    private static void requireText(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(property + " must not be blank");
        }
    }

    public record S3(String bucket, String region) {
    }
}
