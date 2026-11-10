package dfgg.domain.image;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
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
    @DisplayName("버전 폴더는 만들 때마다 현재 서비스 패치를 읽는다")
    void championOf_WhenVersionSourceChanges_UsesCurrentVersion() {
        // given
        AtomicReference<String> version =
                new AtomicReference<>("16.21");
        ImageUrls imageUrls = new ImageUrls("bucket", "region", version::get);
        String before = imageUrls.itemOf(3031L);

        // when
        version.set("16.22");

        // then
        assertThat(before).contains("/16.21/");
        assertThat(imageUrls.itemOf(3031L)).contains("/16.22/");
        assertThat(imageUrls.version()).isEqualTo("16.22");
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

    @Test
    @DisplayName("룬 이미지 URL은 설정 버전이 아니라 추천에 쓴 패치 폴더를 가리킨다")
    void runeOf_WhenPatchGiven_BuildUrlUnderThatPatchNotConfiguredVersion() {
        // given
        ImageUrls imageUrls = new ImageUrls("bucket", "region", "16.18");

        // when
        String url = imageUrls.runeOf("16.21", 8437);

        // then
        assertThat(url).isEqualTo(
                "https://bucket.s3.region.amazonaws.com/" + ImageKeys.rune("16.21", 8437));
    }

    @Test
    @DisplayName("소환사 주문 이미지 URL은 영문 키로 만든다 — 업로드 키와 같다")
    void summonerSpellOf_WhenBuilt_PathEqualsUploadKey() {
        // given
        ImageUrls imageUrls = new ImageUrls("bucket", "region", "16.18");

        // when & then
        assertThat(imageUrls.summonerSpellOf("SummonerFlash")).isEqualTo(
                "https://bucket.s3.region.amazonaws.com/dfgg/images/16.18/summoner-spells/SummonerFlash.png");
    }

    @Test
    @DisplayName("증강 이미지 URL은 설정 버전이 아니라 데이터의 패치 폴더 아래 증강 id로 만든다")
    void augmentOf_WhenPatchGiven_BuildS3UrlByPatchAndAugmentId() {
        // given
        ImageUrls imageUrls = new ImageUrls("bucket", "region", "16.18");

        // when
        String url = imageUrls.augmentOf("16.21", 1373);

        // then
        assertThat(url).isEqualTo(
                "https://bucket.s3.region.amazonaws.com/dfgg/images/16.21/augments/1373.png");
    }
}
