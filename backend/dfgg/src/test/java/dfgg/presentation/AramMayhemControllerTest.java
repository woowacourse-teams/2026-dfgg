package dfgg.presentation;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import dfgg.domain.aram.Augment;
import dfgg.domain.aram.AugmentRarity;
import dfgg.domain.aram.AugmentRepository;
import dfgg.domain.aram.ChampionAugment;
import dfgg.domain.aram.ChampionAugmentRepository;
import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/** HTTP 바인딩부터 패치 선택, 등급별 순위, 이름·이미지 붙이기까지 연결한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AramMayhemControllerTest {

    private static final long ASHE = 22L;
    private static final long GAREN = 86L;

    @LocalServerPort
    private int port;

    @Autowired
    private ChampionRepository championRepository;

    @Autowired
    private AugmentRepository augmentRepository;

    @Autowired
    private ChampionAugmentRepository championAugmentRepository;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        championRepository.save(new Champion(ASHE, "Ashe", Map.of("ko-KR", "애쉬", "en-US", "Ashe"), List.of()));
        championRepository.save(new Champion(GAREN, "Garen", Map.of("ko-KR", "가렌", "en-US", "Garen"), List.of()));
    }

    /** {@code @SpringBootTest}는 롤백하지 않는다. 커밋된 채 남으면 다른 테스트가 깨진다. */
    @AfterEach
    void cleanUp() {
        augmentRepository.deleteAllInBatch();
        championRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("챔피언의 증강을 등급과 순위 순으로 모두 준다")
    void getAugments_ReturnsEveryAugmentByRarityAndRank() {
        // given
        saveAugment("16.21", 1373, "축소 엔진", "Shrink Engine", AugmentRarity.GOLD);
        saveAugment("16.21", 1402, "되풀이", "Recursion", AugmentRarity.PRISMATIC);
        saveAugment("16.21", 1100, "민첩함", "Nimble", AugmentRarity.SILVER);
        saveAugment("16.21", 1200, "벌이다", "Earn It", AugmentRarity.GOLD);
        savePick("16.21", 1373, 16.79);
        savePick("16.21", 1402, 14.14);
        savePick("16.21", 1100, 3.0);
        savePick("16.21", 1200, 20.0);

        // when & then
        given().accept(ContentType.JSON)
                .when().get("/api/aram-mayhem/champions/22/augments")
                .then().statusCode(200)
                .body("patch", equalTo("16.21"))
                .body("augments.augmentId", contains(1100, 1200, 1373, 1402))
                .body("augments.rarity", contains("silver", "gold", "gold", "prismatic"))
                .body("augments.rank", contains(1, 1, 2, 1));
    }

    @Test
    @DisplayName("증강 항목에 이름, 픽률, 패치 폴더의 이미지 주소를 싣는다")
    void getAugments_CarriesNamePickRateAndIcon() {
        // given
        saveAugment("16.21", 1373, "축소 엔진", "Shrink Engine", AugmentRarity.GOLD);
        savePick("16.21", 1373, 16.79);

        // when & then
        given().accept(ContentType.JSON)
                .header("Accept-Language", "ko")
                .when().get("/api/aram-mayhem/champions/22/augments")
                .then().statusCode(200)
                .body("augments[0].name", equalTo("축소 엔진"))
                .body("augments[0].pickRate", equalTo(16.8f))
                .body("augments[0].iconUrl", equalTo(
                        "https://test-bucket.s3.ap-northeast-2.amazonaws.com/dfgg/images/16.21/augments/1373.png"));
    }

    @Test
    @DisplayName("Accept-Language가 없으면 영어 이름을 싣는다")
    void getAugments_WhenNoLanguage_CarriesEnglishName() {
        // given
        saveAugment("16.21", 1373, "축소 엔진", "Shrink Engine", AugmentRarity.GOLD);
        savePick("16.21", 1373, 16.79);

        // when & then
        given().accept(ContentType.JSON)
                .when().get("/api/aram-mayhem/champions/22/augments")
                .then().statusCode(200)
                .body("augments[0].name", equalTo("Shrink Engine"));
    }

    @Test
    @DisplayName("저장된 패치 중 가장 높은 패치만 쓴다")
    void getAugments_UsesLatestPatchOnly() {
        // given
        saveAugment("16.9", 1100, "민첩함", "Nimble", AugmentRarity.SILVER);
        savePick("16.9", 1100, 3.0);
        saveAugment("16.21", 1373, "축소 엔진", "Shrink Engine", AugmentRarity.GOLD);
        savePick("16.21", 1373, 16.79);

        // when & then
        given().accept(ContentType.JSON)
                .when().get("/api/aram-mayhem/champions/22/augments")
                .then().statusCode(200)
                .body("patch", equalTo("16.21"))
                .body("augments.augmentId", contains(1373));
    }

    @Test
    @DisplayName("그 챔피언의 값이 없으면 200에 빈 목록이다")
    void getAugments_WhenChampionHasNoPick_ReturnsEmptyList() {
        // given
        saveAugment("16.21", 1373, "축소 엔진", "Shrink Engine", AugmentRarity.GOLD);
        savePick("16.21", 1373, 16.79);

        // when & then
        given().accept(ContentType.JSON)
                .when().get("/api/aram-mayhem/champions/86/augments")
                .then().statusCode(200)
                .body("patch", equalTo("16.21"))
                .body("augments", empty());
    }

    @Test
    @DisplayName("없는 챔피언이면 400이다")
    void getAugments_WhenChampionUnknown_Returns400() {
        // given
        saveAugment("16.21", 1373, "축소 엔진", "Shrink Engine", AugmentRarity.GOLD);

        // when & then
        given().accept(ContentType.JSON)
                .when().get("/api/aram-mayhem/champions/99999/augments")
                .then().statusCode(400);
    }

    @Test
    @DisplayName("챔피언 ID가 숫자가 아니면 400이다")
    void getAugments_WhenChampionIdNotNumber_Returns400() {
        // when & then
        given().accept(ContentType.JSON)
                .when().get("/api/aram-mayhem/champions/ashe/augments")
                .then().statusCode(400);
    }

    @Test
    @DisplayName("저장된 증강 데이터가 없으면 503이다")
    void getAugments_WhenNoData_Returns503() {
        // when & then
        given().accept(ContentType.JSON)
                .when().get("/api/aram-mayhem/champions/22/augments")
                .then().statusCode(503);
    }

    @Test
    @DisplayName("응답 어디에도 승률이 없다")
    void getAugments_NeverCarriesWinRate() {
        // given
        saveAugment("16.21", 1373, "축소 엔진", "Shrink Engine", AugmentRarity.GOLD);
        savePick("16.21", 1373, 16.79);

        // when
        String body = given().accept(ContentType.JSON)
                .when().get("/api/aram-mayhem/champions/22/augments")
                .then().statusCode(200)
                .extract().asString();

        // then
        assertThat(body.toLowerCase(Locale.ROOT)).doesNotContain("win");
    }

    @Test
    @DisplayName("읽은 이름의 픽률을 요청 순서대로 주고 못 찾은 이름은 따로 준다")
    void lookup_ReturnsPickRatesInRequestOrderWithUnmatchedNames() {
        // given
        saveAugment("16.21", 1373, "축소 엔진", "Shrink Engine", AugmentRarity.GOLD);
        saveAugment("16.21", 1200, "벌이다", "Earn It", AugmentRarity.GOLD);
        savePick("16.21", 1373, 16.79);
        savePick("16.21", 1200, 20.0);

        // when & then
        lookup(22, "{\"names\": [\"축소 엔진\", \"없는증강\", \"벌이다\"]}")
                .then().statusCode(200)
                .body("patch", equalTo("16.21"))
                .body("augments.augmentId", contains(1373, 1200))
                .body("augments.rank", contains(2, 1))
                .body("augments[0].pickRate", equalTo(16.8f))
                .body("augments[0].iconUrl", equalTo(
                        "https://test-bucket.s3.ap-northeast-2.amazonaws.com/dfgg/images/16.21/augments/1373.png"))
                .body("unmatchedNames", contains("없는증강"));
    }

    @Test
    @DisplayName("증강은 있지만 이 챔피언의 값이 없으면 순위와 픽률이 null이다")
    void lookup_WhenChampionHasNoPick_ReturnsNullRankAndPickRate() {
        // given
        saveAugment("16.21", 1402, "되풀이", "Recursion", AugmentRarity.PRISMATIC);

        // when & then
        lookup(22, "{\"names\": [\"되풀이\"]}")
                .then().statusCode(200)
                .body("augments[0].augmentId", equalTo(1402))
                .body("augments[0].rarity", equalTo("prismatic"))
                .body("augments[0]", hasKey("rank"))
                .body("augments[0]", hasKey("pickRate"))
                .body("augments[0].rank", nullValue())
                .body("augments[0].pickRate", nullValue())
                .body("unmatchedNames", empty());
    }

    @Test
    @DisplayName("한글로 읽어도 요청 언어의 이름으로 응답한다")
    void lookup_NameFollowsRequestedLanguage() {
        // given
        saveAugment("16.21", 1373, "축소 엔진", "Shrink Engine", AugmentRarity.GOLD);

        // when & then
        lookup(22, "{\"names\": [\"축소 엔진\"]}")
                .then().statusCode(200)
                .body("augments[0].name", equalTo("Shrink Engine"));
    }

    @Test
    @DisplayName("최신 패치에 없는 증강은 이전 패치에 있어도 찾지 못한다")
    void lookup_SearchesLatestPatchOnly() {
        // given
        saveAugment("16.9", 1100, "민첩함", "Nimble", AugmentRarity.SILVER);
        saveAugment("16.21", 1373, "축소 엔진", "Shrink Engine", AugmentRarity.GOLD);

        // when & then
        lookup(22, "{\"names\": [\"민첩함\"]}")
                .then().statusCode(200)
                .body("augments", empty())
                .body("unmatchedNames", contains("민첩함"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"names\": []}",
            "{\"names\": [\"\"]}",
            "{\"names\": [\"  \"]}",
            "{\"names\": [\"가\", \"나\", \"다\", \"라\"]}"
    })
    @DisplayName("이름이 없거나 비었거나 3개를 넘으면 400이다")
    void lookup_WhenNamesInvalid_Returns400(String body) {
        // given
        saveAugment("16.21", 1373, "축소 엔진", "Shrink Engine", AugmentRarity.GOLD);

        // when & then
        lookup(22, body).then().statusCode(400);
    }

    @Test
    @DisplayName("이름이 100자를 넘으면 400이다")
    void lookup_WhenNameTooLong_Returns400() {
        // given
        saveAugment("16.21", 1373, "축소 엔진", "Shrink Engine", AugmentRarity.GOLD);
        String name = "가".repeat(101);

        // when & then
        lookup(22, "{\"names\": [\"" + name + "\"]}").then().statusCode(400);
    }

    @Test
    @DisplayName("이름이 100자면 받아서 못 찾은 이름으로 돌려준다")
    void lookup_WhenNameAtLimit_ReturnsUnmatched() {
        // given
        saveAugment("16.21", 1373, "축소 엔진", "Shrink Engine", AugmentRarity.GOLD);
        String name = "가".repeat(100);

        // when & then
        lookup(22, "{\"names\": [\"" + name + "\"]}")
                .then().statusCode(200)
                .body("unmatchedNames", contains(name));
    }

    @Test
    @DisplayName("없는 챔피언이면 400이다")
    void lookup_WhenChampionUnknown_Returns400() {
        // given
        saveAugment("16.21", 1373, "축소 엔진", "Shrink Engine", AugmentRarity.GOLD);

        // when & then
        lookup(99999, "{\"names\": [\"축소 엔진\"]}").then().statusCode(400);
    }

    @Test
    @DisplayName("저장된 증강 데이터가 없으면 503이다")
    void lookup_WhenNoData_Returns503() {
        // when & then
        lookup(22, "{\"names\": [\"축소 엔진\"]}").then().statusCode(503);
    }

    @Test
    @DisplayName("응답 어디에도 승률이 없다")
    void lookup_NeverCarriesWinRate() {
        // given
        saveAugment("16.21", 1373, "축소 엔진", "Shrink Engine", AugmentRarity.GOLD);
        savePick("16.21", 1373, 16.79);

        // when
        String body = lookup(22, "{\"names\": [\"축소 엔진\"]}")
                .then().statusCode(200)
                .extract().asString();

        // then
        assertThat(body.toLowerCase(Locale.ROOT)).doesNotContain("win");
    }

    @Test
    @DisplayName("제목 견본은 최신 패치 증강 전체를 증강 ID 순으로 그 언어 이름으로 그려 준다")
    void getTitles_ReturnsEveryAugmentOfLatestPatchInIdOrder() {
        // given
        saveAugment("16.9", 1001, "옛 증강", "Old Augment", AugmentRarity.SILVER);
        saveAugment("16.21", 1402, "되풀이", "Recursion", AugmentRarity.PRISMATIC);
        saveAugment("16.21", 1100, "민첩함", "Nimble", AugmentRarity.SILVER);
        saveAugment("16.21", 1373, "축소 엔진", "Shrink Engine", AugmentRarity.GOLD);

        // when & then
        given().accept(ContentType.JSON)
                .header("Accept-Language", "ko")
                .when().get("/api/aram-mayhem/augment-titles")
                .then().statusCode(200)
                .body("patch", equalTo("16.21"))
                .body("lang", equalTo("ko_KR"))
                .body("font", equalTo("Spoqa Han Sans Neo Regular"))
                .body("fontSize", equalTo(64))
                .body("padding", equalTo(10))
                .body("images.augmentId", contains(1100, 1373, 1402))
                .body("images.name", contains("민첩함", "축소 엔진", "되풀이"))
                .body("images.height", contains(84, 84, 84))
                .body("$", not(hasKey("etag")));
    }

    @Test
    @DisplayName("제목 견본의 png는 폭과 높이가 응답 값과 같은 PNG의 base64다")
    void getTitles_CarriesBase64PngMatchingSize() throws IOException {
        // given
        saveAugment("16.21", 1373, "축소 엔진", "Shrink Engine", AugmentRarity.GOLD);

        // when
        Response response = given().accept(ContentType.JSON)
                .header("Accept-Language", "ko")
                .when().get("/api/aram-mayhem/augment-titles");

        // then
        response.then().statusCode(200);
        byte[] png = Base64.getDecoder().decode(response.path("images[0].png").toString());
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(image.getWidth()).isEqualTo((int) response.path("images[0].width"));
        assertThat(image.getHeight()).isEqualTo(84);
    }

    @Test
    @DisplayName("제목 견본은 Accept-Language로 언어를 고르고 응답 lang은 밑줄 표기로 낸다")
    void getTitles_ChoosesLanguageByAcceptLanguage() {
        // given
        saveAugment("16.21", 1373, "축소 엔진", "Shrink Engine", AugmentRarity.GOLD);

        // when & then
        given().accept(ContentType.JSON)
                .header("Accept-Language", "en-US,en;q=0.9")
                .when().get("/api/aram-mayhem/augment-titles")
                .then().statusCode(200)
                .body("lang", equalTo("en_US"))
                .body("images.name", contains("Shrink Engine"));
    }

    @Test
    @DisplayName("제목 견본은 지정한 패치를 major.minor로 맞춰 그 패치의 증강을 준다")
    void getTitles_WhenPatchGiven_UsesThatPatch() {
        // given
        saveAugment("16.9", 1001, "옛 증강", "Old Augment", AugmentRarity.SILVER);
        saveAugment("16.21", 1373, "축소 엔진", "Shrink Engine", AugmentRarity.GOLD);

        // when & then
        given().accept(ContentType.JSON)
                .header("Accept-Language", "ko")
                .queryParam("patch", "16.9.1")
                .when().get("/api/aram-mayhem/augment-titles")
                .then().statusCode(200)
                .body("patch", equalTo("16.9"))
                .body("images.augmentId", contains(1001));
    }

    @Test
    @DisplayName("요청 언어의 이름이 없는 증강은 다른 언어로 대신 그리지 않고 뺀다")
    void getTitles_WhenNameMissingInLanguage_SkipsAugment() {
        // given
        saveAugment("16.21", 1373, "축소 엔진", "Shrink Engine", AugmentRarity.GOLD);
        augmentRepository.save(new Augment("16.21", 1500, Map.of("ko-KR", "한글만"), AugmentRarity.GOLD));

        // when & then
        given().accept(ContentType.JSON)
                .header("Accept-Language", "en")
                .when().get("/api/aram-mayhem/augment-titles")
                .then().statusCode(200)
                .body("images.augmentId", contains(1373));
    }

    @Test
    @DisplayName("그릴 견본이 없으면 200에 빈 목록이다")
    void getTitles_WhenNothingToDraw_ReturnsEmptyList() {
        // given
        augmentRepository.save(new Augment("16.21", 1500, Map.of("ko-KR", "한글만"), AugmentRarity.GOLD));

        // when & then
        given().accept(ContentType.JSON)
                .header("Accept-Language", "en")
                .when().get("/api/aram-mayhem/augment-titles")
                .then().statusCode(200)
                .body("images", empty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ja", "fr-FR"})
    @DisplayName("제목 견본은 Accept-Language가 ko가 아니면 다른 API처럼 영어로 그린다")
    void getTitles_WhenAcceptLanguageNotKorean_DrawsEnglish(String acceptLanguage) {
        // given
        saveAugment("16.21", 1373, "축소 엔진", "Shrink Engine", AugmentRarity.GOLD);

        // when & then
        given().accept(ContentType.JSON)
                .header("Accept-Language", acceptLanguage)
                .when().get("/api/aram-mayhem/augment-titles")
                .then().statusCode(200)
                .body("lang", equalTo("en_US"))
                .body("images.name", contains("Shrink Engine"));
    }

    @Test
    @DisplayName("제목 견본은 Accept-Language가 없으면 영어로 그린다")
    void getTitles_WhenAcceptLanguageMissing_DrawsEnglish() {
        // given
        saveAugment("16.21", 1373, "축소 엔진", "Shrink Engine", AugmentRarity.GOLD);

        // when & then
        given().accept(ContentType.JSON)
                .when().get("/api/aram-mayhem/augment-titles")
                .then().statusCode(200)
                .body("lang", equalTo("en_US"))
                .body("images.name", contains("Shrink Engine"));
    }

    @Test
    @DisplayName("제목 견본의 patch 형식이 틀리면 400이다")
    void getTitles_WhenPatchMalformed_Returns400() {
        // given
        saveAugment("16.21", 1373, "축소 엔진", "Shrink Engine", AugmentRarity.GOLD);

        // when & then
        given().accept(ContentType.JSON)
                .header("Accept-Language", "ko")
                .queryParam("patch", "latest")
                .when().get("/api/aram-mayhem/augment-titles")
                .then().statusCode(400);
    }

    @Test
    @DisplayName("지정한 패치의 데이터가 없으면 404다")
    void getTitles_WhenPatchNotStored_Returns404() {
        // given
        saveAugment("16.21", 1373, "축소 엔진", "Shrink Engine", AugmentRarity.GOLD);

        // when & then
        given().accept(ContentType.JSON)
                .header("Accept-Language", "ko")
                .queryParam("patch", "15.1")
                .when().get("/api/aram-mayhem/augment-titles")
                .then().statusCode(404);
    }

    @Test
    @DisplayName("제목 견본은 강한 ETag와 Vary: Accept-Language를 주고 patch를 생략하면 매번 다시 확인하라고 한다")
    void getTitles_WhenPatchOmitted_CarriesEtagAndNoCache() {
        // given
        saveAugment("16.21", 1373, "축소 엔진", "Shrink Engine", AugmentRarity.GOLD);

        // when & then
        given().accept(ContentType.JSON)
                .header("Accept-Language", "ko")
                .when().get("/api/aram-mayhem/augment-titles")
                .then().statusCode(200)
                .header("ETag", matchesPattern("^\"[0-9a-f]+\"$"))
                .header("Cache-Control", equalTo("no-cache"))
                .header("Vary", containsString("Accept-Language"));
    }

    @Test
    @DisplayName("제목 견본은 patch를 지정하면 하루 동안 캐시해도 된다고 한다")
    void getTitles_WhenPatchGiven_AllowsCachingForADay() {
        // given
        saveAugment("16.21", 1373, "축소 엔진", "Shrink Engine", AugmentRarity.GOLD);

        // when & then
        given().accept(ContentType.JSON)
                .header("Accept-Language", "ko")
                .queryParam("patch", "16.21")
                .when().get("/api/aram-mayhem/augment-titles")
                .then().statusCode(200)
                .header("Cache-Control", equalTo("max-age=86400, public"));
    }

    @Test
    @DisplayName("같은 ETag로 다시 요청하면 본문 없이 304와 ETag·Cache-Control을 준다")
    void getTitles_WhenEtagMatches_Returns304WithHeaders() {
        // given
        saveAugment("16.21", 1373, "축소 엔진", "Shrink Engine", AugmentRarity.GOLD);
        String etag = given().header("Accept-Language", "ko").queryParam("patch", "16.21")
                .when().get("/api/aram-mayhem/augment-titles")
                .then().statusCode(200)
                .extract().header("ETag");

        // when & then
        String body = given().header("Accept-Language", "ko").queryParam("patch", "16.21")
                .header("If-None-Match", etag)
                .when().get("/api/aram-mayhem/augment-titles")
                .then().statusCode(304)
                .header("ETag", equalTo(etag))
                .header("Cache-Control", equalTo("max-age=86400, public"))
                .extract().asString();
        assertThat(body).isEmpty();
    }

    @Test
    @DisplayName("다른 ETag로 요청하면 200과 본문을 준다")
    void getTitles_WhenEtagDiffers_Returns200() {
        // given
        saveAugment("16.21", 1373, "축소 엔진", "Shrink Engine", AugmentRarity.GOLD);

        // when & then
        given().header("Accept-Language", "ko")
                .header("If-None-Match", "\"stale\"")
                .when().get("/api/aram-mayhem/augment-titles")
                .then().statusCode(200)
                .body("images.augmentId", contains(1373));
    }

    @Test
    @DisplayName("저장된 이름이 바뀌면 ETag도 바뀐다")
    void getTitles_WhenNameChanges_ChangesEtag() {
        // given
        saveAugment("16.21", 1373, "축소 엔진", "Shrink Engine", AugmentRarity.GOLD);
        String before = titlesEtag("ko");
        augmentRepository.deleteAllInBatch();
        saveAugment("16.21", 1373, "축소 엔진!", "Shrink Engine", AugmentRarity.GOLD);

        // when
        String after = titlesEtag("ko");

        // then
        assertThat(after).isNotEqualTo(before);
    }

    @Test
    @DisplayName("언어가 다르면 ETag도 다르다")
    void getTitles_WhenLanguageDiffers_ChangesEtag() {
        // given
        saveAugment("16.21", 1373, "축소 엔진", "Shrink Engine", AugmentRarity.GOLD);

        // when & then
        assertThat(titlesEtag("ko")).isNotEqualTo(titlesEtag("en"));
    }

    @Test
    @DisplayName("저장된 증강 데이터가 없으면 503이다")
    void getTitles_WhenNoData_Returns503() {
        // when & then
        given().accept(ContentType.JSON)
                .header("Accept-Language", "ko")
                .when().get("/api/aram-mayhem/augment-titles")
                .then().statusCode(503);
    }

    private String titlesEtag(String acceptLanguage) {
        return given().header("Accept-Language", acceptLanguage)
                .when().get("/api/aram-mayhem/augment-titles")
                .then().statusCode(200)
                .extract().header("ETag");
    }

    private Response lookup(int championId, String body) {
        return given().contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body(body)
                .when().post("/api/aram-mayhem/champions/{championId}/augments/lookup", championId);
    }

    private void saveAugment(String patch, int augmentId, String koreanName, String englishName,
                             AugmentRarity rarity) {
        Map<String, String> name = Map.of("ko-KR", koreanName, "en-US", englishName);
        augmentRepository.save(new Augment(patch, augmentId, name, rarity));
    }

    private void savePick(String patch, int augmentId, double pickRate) {
        championAugmentRepository.save(new ChampionAugment(patch, (int) ASHE, augmentId, 100, pickRate));
    }
}
