package dfgg.presentation;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import dfgg.domain.champion.ChampionRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

/**
 * 클라이언트가 ddragon {@code champion.json} 대신 쓰는 챔피언 목록.
 * 검색창과 밴픽 화면이 id·영문 키·한글 이름·이미지를 한 번에 받는다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Sql("/sql/champion-list-test-data.sql")
class ChampionQueryControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ChampionRepository championRepository;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    /** {@code @SpringBootTest}는 롤백하지 않는다. 커밋된 채 남으면 다른 테스트가 깨진다. */
    @AfterEach
    void cleanUp() {
        championRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("저장된 챔피언을 모두 돌려준다")
    void getChampions_WhenChampionsExist_ReturnEveryChampion() {
        // when & then
        given().accept(ContentType.JSON)
                .when().get("/api/champions")
                .then().statusCode(200)
                .body("champions", hasSize(3));
    }

    @Test
    @DisplayName("챔피언마다 id·영문 키·한글 이름과 영문 키로 만든 이미지 URL을 싣는다")
    void getChampions_WhenChampionExists_CarryIdKeyNameAndImageUrl() {
        // when & then
        given().accept(ContentType.JSON)
                .when().get("/api/champions")
                .then().statusCode(200)
                .body("champions.find { it.riotKey == 'MonkeyKing' }.id", equalTo(62))
                .body("champions.find { it.riotKey == 'MonkeyKing' }.name", equalTo("손오공"))
                .body("champions.find { it.riotKey == 'MonkeyKing' }.imageUrl",
                        equalTo("https://test-bucket.s3.ap-northeast-2.amazonaws.com/dfgg/images/99.1/champions/MonkeyKing.png"));
    }
}
