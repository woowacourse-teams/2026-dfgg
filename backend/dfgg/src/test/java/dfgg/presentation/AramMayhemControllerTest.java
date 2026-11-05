package dfgg.presentation;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;

import dfgg.domain.aram.Augment;
import dfgg.domain.aram.AugmentRarity;
import dfgg.domain.aram.AugmentRepository;
import dfgg.domain.aram.ChampionAugment;
import dfgg.domain.aram.ChampionAugmentRepository;
import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

    private void saveAugment(String patch, int augmentId, String koreanName, String englishName,
                             AugmentRarity rarity) {
        Map<String, String> name = Map.of("ko-KR", koreanName, "en-US", englishName);
        augmentRepository.save(new Augment(patch, augmentId, name, rarity));
    }

    private void savePick(String patch, int augmentId, double pickRate) {
        championAugmentRepository.save(new ChampionAugment(patch, (int) ASHE, augmentId, 100, pickRate));
    }
}
