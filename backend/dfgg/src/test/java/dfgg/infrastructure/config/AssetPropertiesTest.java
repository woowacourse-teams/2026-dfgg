package dfgg.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AssetPropertiesTest {

    @Test
    @DisplayName("업로드와 같은 버킷·리전과 주어진 버전으로 이미지 URL을 만든다")
    void imageUrls_WhenVersionGiven_BuildUrlsFromBucketRegionAndVersion() {
        // given
        AssetProperties properties = new AssetProperties(
                new AssetProperties.S3("bucket", "region"));

        // when
        String url = properties.imageUrls("16.18").itemOf(3031L);

        // then
        assertThat(url).isEqualTo(
                "https://bucket.s3.region.amazonaws.com/dfgg/images/16.18/items/3031.png");
    }

    @Test
    @DisplayName("버킷이 비면 기동을 거부하고 어느 설정인지 알려준다 — 응답 URL을 만들 수 없다")
    void constructor_WhenBucketBlank_ThrowNamingTheProperty() {
        // when & then
        assertThatThrownBy(() -> new AssetProperties(new AssetProperties.S3(" ", "region")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("assets.s3.bucket");
    }

    @Test
    @DisplayName("리전이 비면 기동을 거부하고 어느 설정인지 알려준다")
    void constructor_WhenRegionBlank_ThrowNamingTheProperty() {
        // when & then
        assertThatThrownBy(() -> new AssetProperties(new AssetProperties.S3("bucket", "")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("assets.s3.region");
    }

    @Test
    @DisplayName("assets 설정이 통째로 없어도 버킷 설정 이름으로 알려준다")
    void constructor_WhenS3SectionMissing_ThrowNamingTheBucketProperty() {
        // when & then
        assertThatThrownBy(() -> new AssetProperties(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("assets.s3.bucket");
    }
}
