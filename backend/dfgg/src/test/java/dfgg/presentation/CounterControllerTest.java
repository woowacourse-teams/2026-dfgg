package dfgg.presentation;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Sql("/sql/counter-controller-test-data.sql")
class CounterControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM champion_counters");
        jdbcTemplate.update("DELETE FROM champion_position_stats");
    }

    @Test
    @DisplayName("상대 공개 픽마다 라인 분포와 내 라인 카운터를 응답한다")
    void recommend_WhenValidRequest_RespondDistributionAndCountersPerEnemy() {
        given()
                .contentType(ContentType.JSON)
                .header(HttpHeaders.ACCEPT_LANGUAGE, "ko")
                .body("""
                        {"myPosition": "MID", "enemies": [103, 122], "allies": [64], "bans": [157]}
                        """)
                .when().post("/api/counters")
                .then().statusCode(200)
                .body("patch", equalTo("16.10"))
                .body("myPosition", equalTo("MID"))
                .body("enemies.championId", contains(103, 122))
                .body("enemies[0].name", equalTo("아리"))
                .body("enemies[0].positions", hasSize(5))
                .body("enemies[0].positions[0].position", equalTo("MID"))
                .body("enemies[0].positions[0].rate", equalTo(90.0f))
                .body("enemies[0].positions[0]", not(hasKey("games")))
                .body("enemies[0].counters.championId", contains(90, 1))
                .body("enemies[0].counters[0].name", equalTo("말자하"))
                .body("enemies[0].counters[0]", not(hasKey("reason")))
                .body("enemies[1].counters", empty());
    }

    @Test
    @DisplayName("아군·밴은 생략할 수 있다")
    void recommend_WhenAlliesAndBansOmitted_Respond200() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"myPosition": "MID", "enemies": [103]}
                        """)
                .when().post("/api/counters")
                .then().statusCode(200)
                .body("enemies[0].counters.championId", contains(90, 64, 1));
    }

    @Test
    @DisplayName("카운터 데이터가 바뀌면 재시작 없이 다음 요청부터 반영된다")
    void recommend_WhenCountersChangedBySql_ReflectOnNextRequest() {
        // given
        String body = """
                {"myPosition": "MID", "enemies": [103]}
                """;
        given().contentType(ContentType.JSON).body(body)
                .when().post("/api/counters")
                .then().statusCode(200)
                .body("enemies[0].counters.championId", contains(90, 64, 1));
        jdbcTemplate.update("DELETE FROM champion_counters WHERE enemy_champion_id = 103 AND counter_champion_id = 90");
        jdbcTemplate.update(
                "INSERT INTO champion_counters (enemy_champion_id, position, counter_champion_id, rank) VALUES (103, 'MID', 86, 4)");

        // when & then
        given().contentType(ContentType.JSON).body(body)
                .when().post("/api/counters")
                .then().statusCode(200)
                .body("enemies[0].counters.championId", contains(64, 1, 86));
    }

    @Test
    @DisplayName("내 포지션이 없으면 400이다")
    void recommend_WhenMyPositionMissing_Respond400() {
        badRequest("""
                {"enemies": [103]}
                """);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MIDDLE", "mid", "ADC"})
    @DisplayName("내 포지션이 다섯 라인 이름이 아니면 400이다")
    void recommend_WhenMyPositionInvalid_Respond400(String position) {
        badRequest("""
                {"myPosition": "%s", "enemies": [103]}
                """.formatted(position));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"myPosition\": \"MID\", \"enemies\": []}",
            "{\"myPosition\": \"MID\"}"
    })
    @DisplayName("상대 픽이 없거나 생략돼도 400이 아니다 — 블루 1픽은 상대 정보가 없다")
    void recommend_WhenNoEnemy_Respond200WithEmptyEnemies(String body) {
        given()
                .contentType(ContentType.JSON)
                .body(body)
                .when().post("/api/counters")
                .then().statusCode(200)
                .body("enemies", empty());
    }

    @Test
    @DisplayName("상대 픽이 6명 이상이면 400이다")
    void recommend_WhenSixEnemies_Respond400() {
        badRequest("""
                {"myPosition": "MID", "enemies": [103, 122, 90, 64, 1, 86]}
                """);
    }

    @Test
    @DisplayName("아군 픽이 5명 이상이면 400이다 — 나를 뺀 아군은 최대 4명이다")
    void recommend_WhenFiveAllies_Respond400() {
        badRequest("""
                {"myPosition": "MID", "enemies": [103], "allies": [122, 90, 64, 1, 86]}
                """);
    }

    @Test
    @DisplayName("밴이 11개 이상이면 400이다")
    void recommend_WhenElevenBans_Respond400() {
        badRequest("""
                {"myPosition": "MID", "enemies": [103], "bans": [201, 202, 203, 204, 205, 206, 207, 208, 209, 210, 211]}
                """);
    }

    @Test
    @DisplayName("알 수 없는 챔피언이 있으면 400이고, 그 id를 알려준다")
    void recommend_WhenUnknownChampion_Respond400WithChampionId() {
        badRequest("""
                {"myPosition": "MID", "enemies": [103], "bans": [99999]}
                """)
                .body("detail", containsString("99999"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"myPosition\": \"MID\", \"enemies\": [103], \"allies\": [103]}",
            "{\"myPosition\": \"MID\", \"enemies\": [103], \"bans\": [103]}",
            "{\"myPosition\": \"MID\", \"enemies\": [103, 103]}",
            "{\"myPosition\": \"MID\", \"enemies\": [103], \"allies\": [64], \"bans\": [64]}"
    })
    @DisplayName("같은 챔피언이 아군·적·밴에 두 번 나오면 400이다")
    void recommend_WhenChampionDuplicated_Respond400(String body) {
        badRequest(body)
                .body("detail", not(empty()));
    }

    private ValidatableResponse badRequest(String body) {
        return given()
                .contentType(ContentType.JSON)
                .body(body)
                .when().post("/api/counters")
                .then().statusCode(400);
    }
}
