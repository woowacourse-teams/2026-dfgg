package dfgg.presentation;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

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
@Sql("/sql/today-pick-test-data.sql")
class TodayPickControllerTest {

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
        jdbcTemplate.update("DELETE FROM pro_picks");
        jdbcTemplate.update("DELETE FROM pro_players");
        jdbcTemplate.update("DELETE FROM pro_tournaments");
        // 커밋되는 테스트라 픽스처의 9000번대 카탈로그 행도 지운다. 다른 테스트가 테이블 행 수를 센다.
        jdbcTemplate.update("DELETE FROM runes WHERE rune_id BETWEEN 9000 AND 9999");
        jdbcTemplate.update("DELETE FROM summoner_spells WHERE spell_id BETWEEN 9000 AND 9999");
        jdbcTemplate.update("DELETE FROM items WHERE item_id BETWEEN 9000 AND 9999");
        jdbcTemplate.update("DELETE FROM champions WHERE champion_id BETWEEN 9000 AND 9999");
    }

    @Test
    @DisplayName("최근 대회의 내 라인 픽을 대회·선수·챔피언·주문·아이템·룬과 함께 응답한다")
    void todayPicks_WhenPositionGiven_RespondPicksOfLatestTournament() {
        given()
                .contentType(ContentType.JSON)
                .header(HttpHeaders.ACCEPT_LANGUAGE, "ko")
                .body("""
                        {"position": "MID"}
                        """)
                .when().post("/api/today-picks")
                .then().statusCode(200)
                .body("tournament.name", equalTo("2026 Worlds Main Event"))
                .body("tournament.league", equalTo("World Championship"))
                .body("tournament.startDate", equalTo("2026-10-24"))
                .body("position", equalTo("MID"))
                .body("picks.player.name", containsInAnyOrder("Faker", "Chovy"))
                .body("picks.find { it.player.name == 'Faker' }.playedAt", equalTo("2026-10-27"))
                .body("picks.find { it.player.name == 'Faker' }.champion.name", equalTo("테스트챔피언"))
                .body("picks.find { it.player.name == 'Faker' }.spells.name", contains("점멸", "점화"))
                .body("picks.find { it.player.name == 'Faker' }.items.id", contains(9001, 9002))
                .body("picks.find { it.player.name == 'Faker' }.runes.primaryTree.name", equalTo("트리"))
                .body("picks.find { it.player.name == 'Faker' }.runes.shards.id", contains(5008, 5011));
    }

    @Test
    @DisplayName("밴은 중복을 허용하고, 밴된 챔피언의 픽은 빠진다")
    void todayPicks_WhenBansGiven_ExcludeBannedChampions() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"position": "MID", "bans": [9002, 9002]}
                        """)
                .when().post("/api/today-picks")
                .then().statusCode(200)
                .body("picks.champion.id", contains(9001));
    }

    @Test
    @DisplayName("대회 픽 데이터가 없으면 대회는 null, 픽은 빈 목록이다")
    void todayPicks_WhenNoData_RespondNullTournament() {
        // given
        jdbcTemplate.update("DELETE FROM pro_picks");

        // when & then
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"position": "MID"}
                        """)
                .when().post("/api/today-picks")
                .then().statusCode(200)
                .body("tournament", nullValue())
                .body("position", equalTo("MID"))
                .body("picks", empty());
    }

    @Test
    @DisplayName("position이 없으면 400이다")
    void todayPicks_WhenPositionMissing_Respond400() {
        badRequest("""
                {"bans": [9001]}
                """);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MIDDLE", "mid", "ADC"})
    @DisplayName("position이 다섯 라인 이름이 아니면 400이다")
    void todayPicks_WhenPositionInvalid_Respond400(String position) {
        badRequest("""
                {"position": "%s"}
                """.formatted(position));
    }

    @Test
    @DisplayName("밴이 11개 이상이면 400이다 — 양 팀 5개씩 10개까지다")
    void todayPicks_WhenElevenBans_Respond400() {
        badRequest("""
                {"position": "MID", "bans": [9001, 9001, 9001, 9001, 9001, 9001, 9001, 9001, 9001, 9001, 9001]}
                """)
                .body("detail", containsString("bans"));
    }

    @Test
    @DisplayName("알 수 없는 챔피언을 밴하면 400이고, 그 id를 알려준다")
    void todayPicks_WhenUnknownChampionBanned_Respond400WithId() {
        badRequest("""
                {"position": "MID", "bans": [9001, 99999]}
                """)
                .body("detail", containsString("99999"));
    }

    @Test
    @DisplayName("밴이 숫자가 아니면 400이다")
    void todayPicks_WhenBanNotNumber_Respond400() {
        badRequest("""
                {"position": "MID", "bans": ["Ahri"]}
                """);
    }

    @Test
    @DisplayName("밴을 생략할 수 있다")
    void todayPicks_WhenBansOmitted_Respond200() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"position": "MID"}
                        """)
                .when().post("/api/today-picks")
                .then().statusCode(200)
                .body("picks", hasSize(2));
    }

    @Test
    @DisplayName("GET으로는 더 이상 받지 않는다")
    void todayPicks_WhenGet_Respond405() {
        given()
                .queryParam("position", "MID")
                .when().get("/api/today-picks")
                .then().statusCode(405);
    }

    private ValidatableResponse badRequest(String body) {
        return given()
                .contentType(ContentType.JSON)
                .body(body)
                .when().post("/api/today-picks")
                .then().statusCode(400);
    }
}
