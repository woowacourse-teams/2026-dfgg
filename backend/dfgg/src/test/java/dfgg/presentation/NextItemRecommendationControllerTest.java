package dfgg.presentation;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import dfgg.domain.champion.ChampionRepository;
import dfgg.domain.item.ItemRepository;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
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
 * v3 요청의 입력 오류가 HTTP 상태로 어떻게 나가는지 본다.
 * <p>
 * 클라이언트의 입력 실수가 500으로 나가면 서버 장애와 구분할 수 없고, 모니터링에서도 섞인다.
 * 반대로 서버 내부의 불변식 위반({@code IllegalArgumentException})은 400으로 둔갑시키지 않는다 —
 * 그건 요청이 아니라 코드의 문제다.
 * <p>
 * 오류 본문은 RFC 9457 {@code ProblemDetail}이다. 상태 코드만으로는 "챔피언 이름이 틀렸다"와
 * "구매 목록에 null이 있다"를 구분할 수 없으므로 사유를 {@code detail}에 싣는다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Sql("/sql/v3-recommendation-test-data.sql")
class NextItemRecommendationControllerTest {

    private static final String ALLIES = """
            [
              {"name": "징크스", "position": "BOTTOM"}, {"name": "쓰레쉬", "position": "SUPPORT"},
              {"name": "리신", "position": "JUNGLE"}, {"name": "오른", "position": "TOP"}
            ]""";
    private static final String ENEMIES = """
            [
              {"name": "람머스", "position": "TOP"}, {"name": "아리", "position": "MID"},
              {"name": "케이틀린", "position": "BOTTOM"}, {"name": "레오나", "position": "SUPPORT"},
              {"name": "엘리스", "position": "JUNGLE"}
            ]""";

    @LocalServerPort
    private int port;

    @Autowired
    private NormalizedMatchParticipantRepository participantRepository;
    @Autowired
    private ChampionRepository championRepository;
    @Autowired
    private ItemRepository itemRepository;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    /** {@code @SpringBootTest}는 롤백하지 않는다. 커밋된 채 남으면 다른 테스트가 깨진다. */
    @AfterEach
    void cleanUp() {
        participantRepository.deleteAllInBatch();
        championRepository.deleteAllInBatch();
        itemRepository.deleteAllInBatch();
    }

    private String body(String myChampionName, String purchasedItemIds, String allies, String enemies) {
        return """
                {
                  "myChampion": {"name": "%s", "position": "MID"},
                  "purchasedItemIds": %s,
                  "allies": %s,
                  "enemies": %s,
                  "tier": "EMERALD",
                  "patch": "16.17"
                }""".formatted(myChampionName, purchasedItemIds, allies, enemies);
    }

    @Test
    @DisplayName("모르는 챔피언 이름은 400이다 — 서버 장애가 아니라 입력 오류다")
    void recommendV3_WhenChampionNameIsUnknown_ReturnsBadRequest() {
        // given
        String request = body("야스우", "[]", ALLIES, ENEMIES);

        // when & then
        given().contentType(ContentType.JSON).body(request)
                .when().post("/api/recommendations/v3")
                .then().statusCode(400)
                .contentType("application/problem+json")
                .body("detail", containsString("야스우"));
    }

    @Test
    @DisplayName("내 챔피언이 아군 목록에도 있으면 400이다")
    void recommendV3_WhenMyChampionIsAlsoAnAlly_ReturnsBadRequest() {
        // given
        String alliesWithMyChampion = ALLIES.replace("징크스", "야스오");
        String request = body("야스오", "[]", alliesWithMyChampion, ENEMIES);

        // when & then
        given().contentType(ContentType.JSON).body(request)
                .when().post("/api/recommendations/v3")
                .then().statusCode(400)
                .body("detail", containsString("야스오"));
    }

    @Test
    @DisplayName("구매 아이템 목록에 null이 있으면 400이다")
    void recommendV3_WhenPurchasedItemIdsContainNull_ReturnsBadRequest() {
        // given
        String request = body("야스오", "[3031, null]", ALLIES, ENEMIES);

        // when & then
        given().contentType(ContentType.JSON).body(request)
                .when().post("/api/recommendations/v3")
                .then().statusCode(400)
                .body("detail", containsString("구매한 아이템 ID에 null이 들어갈 수 없습니다"));
    }

    @Test
    @DisplayName("추천할 게 없으면 404이고 어느 챔피언·포지션인지 알려준다")
    void recommendV3_WhenNothingToRecommend_ReturnsNotFoundWithReason() {
        // given: 집계를 돌리지 않아 통계가 비어 있고, 람머스 탑의 전개 표본도 없다
        String request = """
                {
                  "myChampion": {"name": "람머스", "position": "TOP"},
                  "purchasedItemIds": [],
                  "allies": %s,
                  "enemies": [
                    {"name": "야스오", "position": "MID"}, {"name": "아리", "position": "MID"},
                    {"name": "케이틀린", "position": "BOTTOM"}, {"name": "레오나", "position": "SUPPORT"},
                    {"name": "엘리스", "position": "JUNGLE"}
                  ],
                  "tier": "EMERALD",
                  "patch": "16.17"
                }""".formatted(ALLIES);

        // when & then
        given().contentType(ContentType.JSON).body(request)
                .when().post("/api/recommendations/v3")
                .then().statusCode(404)
                .body("detail", containsString("람머스"));
    }
}
