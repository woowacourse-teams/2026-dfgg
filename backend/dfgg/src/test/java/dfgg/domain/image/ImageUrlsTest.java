package dfgg.domain.image;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ImageUrlsTest {

    @Test
    @DisplayName("챔피언 이미지 URL은 버킷·리전의 S3 주소 아래 영문 키로 만든다")
    void championOf_WhenRiotKeyGiven_BuildS3UrlByRiotKey() {
        // given
        ImageUrls imageUrls = new ImageUrls("bucket", "region", "16.18");

        // when
        String url = imageUrls.championOf("MonkeyKing");

        // then
        assertThat(url).isEqualTo(
                "https://bucket.s3.region.amazonaws.com/dfgg/images/16.18/champions/MonkeyKing.png");
    }

    @Test
    @DisplayName("아이템 이미지 URL은 버킷·리전의 S3 주소 아래 아이템 id로 만든다")
    void itemOf_WhenItemIdGiven_BuildS3UrlByItemId() {
        // given
        ImageUrls imageUrls = new ImageUrls("bucket", "region", "16.18");

        // when
        String url = imageUrls.itemOf(3031L);

        // then
        assertThat(url).isEqualTo(
                "https://bucket.s3.region.amazonaws.com/dfgg/images/16.18/items/3031.png");
    }

    @Test
    @DisplayName("URL 경로는 업로드 키와 같다 — 어긋나면 응답이 없는 객체를 가리킨다")
    void championOf_WhenBuilt_PathEqualsUploadKey() {
        // given
        ImageUrls imageUrls = new ImageUrls(" test-bucket ", " region ", " 99.1 ");

        // when
        String url = imageUrls.championOf("Aatrox");

        // then
        assertThat(url).isEqualTo(
                "https://test-bucket.s3.region.amazonaws.com/" + ImageKeys.champion("99.1", "Aatrox"));
    }
}
