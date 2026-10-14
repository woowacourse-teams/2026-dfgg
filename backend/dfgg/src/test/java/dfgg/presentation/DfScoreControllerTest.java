package dfgg.presentation;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/**
 * 채점 대상이 아닌 경기는 오류가 아니다. 200으로 응답하고 모든 참가자의 점수·배지를 비운다.
 * <p>
 * 형식 오류(누락·음수·잘못된 포지션)는 언제나 400이다.
 * 내용 모순(팀 구성, 킬 관여, 오브젝트 피해)은 채점 대상 경기에서만 따진다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DfScoreControllerTest {

    private static final List<String> POSITIONS = List.of("TOP", "JUNGLE", "MID", "BOTTOM", "SUPPORT");

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    private int teamIdOf(int index) {
        if (index < 5) {
            return 100;
        }
        return 200;
    }

    private String participant(int index) {
        int teamId = teamIdOf(index);
        return """
                {"puuid": "p%d", "teamId": %d, "position": "%s", "win": %b,
                 "kills": 3, "deaths": 4, "assists": 5,
                 "totalDamageDealtToChampions": 20000, "totalDamageTaken": 18000, "goldEarned": 11000,
                 "visionScore": 20, "damageDealtToTurrets": 3000, "damageDealtToObjectives": 5000}"""
                .formatted(index + 1, teamId, POSITIONS.get(index % 5), teamId == 100);
    }

    /** 팀마다 포지션 5개가 하나씩, 각자 3킬 5어시스트인 정상 경기의 참가자 10명. 팀 킬은 15다. */
    private List<String> tenParticipants() {
        return new ArrayList<>(IntStream.range(0, 10)
                .mapToObj(this::participant)
                .toList());
    }

    private String body(int queueId, int gameDurationSec, int participantCount) {
        return body(queueId, gameDurationSec, IntStream.range(0, participantCount)
                .mapToObj(this::participant)
                .toList());
    }

    private String body(int queueId, int gameDurationSec, List<String> participants) {
        return """
                {"queueId": %d, "gameDurationSec": %d, "participants": [%s]}"""
                .formatted(queueId, gameDurationSec, String.join(", ", participants));
    }

    @Test
    @DisplayName("지원하지 않는 큐는 모든 참가자의 점수와 배지를 null로 응답한다")
    void score_WhenQueueIsNotSupported_ReturnNullForEveryParticipant() {
        // when & then
        given().contentType(ContentType.JSON)
                .body(body(450, 1800, 10))
                .when().post("/api/df-score")
                .then().statusCode(200)
                .body("participants", hasSize(10))
                .body("participants.puuid", contains("p1", "p2", "p3", "p4", "p5", "p6", "p7", "p8", "p9", "p10"))
                .body("participants.score", everyItem(nullValue()))
                .body("participants.badge", everyItem(nullValue()));
    }

    @Test
    @DisplayName("15분 미만인 경기는 모든 참가자의 점수와 배지를 null로 응답한다")
    void score_WhenShorterThanFifteenMinutes_ReturnNullForEveryParticipant() {
        // when & then
        given().contentType(ContentType.JSON)
                .body(body(420, 899, 10))
                .when().post("/api/df-score")
                .then().statusCode(200)
                .body("participants", hasSize(10))
                .body("participants.score", everyItem(nullValue()))
                .body("participants.badge", everyItem(nullValue()));
    }

    @Test
    @DisplayName("참가자가 10명이 아니면 받은 참가자 모두의 점수와 배지를 null로 응답한다")
    void score_WhenParticipantCountIsNotTen_ReturnNullForEveryParticipant() {
        // when & then
        given().contentType(ContentType.JSON)
                .body(body(420, 1800, 9))
                .when().post("/api/df-score")
                .then().statusCode(200)
                .body("participants", hasSize(9))
                .body("participants.score", everyItem(nullValue()))
                .body("participants.badge", everyItem(nullValue()));
    }

    @Test
    @DisplayName("참가자 값이 빠지면 400과 필드별 사유로 응답한다")
    void score_WhenParticipantValueIsMissing_ReturnBadRequest() {
        // given
        List<String> participants = tenParticipants();
        participants.set(0, participant(0).replace("\"kills\": 3", "\"kills\": null"));

        // when & then
        given().contentType(ContentType.JSON)
                .body(body(420, 1800, participants))
                .when().post("/api/df-score")
                .then().statusCode(400)
                .body("detail", containsString("participants[0].kills"));
    }

    @Test
    @DisplayName("음수 값이 있으면 400으로 응답한다")
    void score_WhenParticipantValueIsNegative_ReturnBadRequest() {
        // given
        List<String> participants = tenParticipants();
        participants.set(2, participant(2).replace("\"deaths\": 4", "\"deaths\": -1"));

        // when & then
        given().contentType(ContentType.JSON)
                .body(body(420, 1800, participants))
                .when().post("/api/df-score")
                .then().statusCode(400)
                .body("detail", containsString("participants[2].deaths"));
    }

    @Test
    @DisplayName("포지션이 TOP·JUNGLE·MID·BOTTOM·SUPPORT가 아니면 400으로 응답한다")
    void score_WhenPositionIsNotSupported_ReturnBadRequest() {
        // given
        List<String> participants = tenParticipants();
        participants.set(2, participant(2).replace("\"MID\"", "\"MIDDLE\""));

        // when & then
        given().contentType(ContentType.JSON)
                .body(body(420, 1800, participants))
                .when().post("/api/df-score")
                .then().statusCode(400)
                .body("detail", containsString("participants[2].position"));
    }

    @Test
    @DisplayName("채점 대상 경기에서 한 팀에 같은 포지션이 둘이면 400으로 응답한다")
    void score_WhenPositionIsDuplicatedInTeam_ReturnBadRequest() {
        // given
        List<String> participants = tenParticipants();
        participants.set(4, participant(4).replace("\"SUPPORT\"", "\"MID\""));

        // when & then
        given().contentType(ContentType.JSON)
                .body(body(420, 1800, participants))
                .when().post("/api/df-score")
                .then().statusCode(400)
                .body("detail", containsString("포지션"));
    }

    @Test
    @DisplayName("채점 대상 경기에서 한 팀이 5명이 아니면 400으로 응답한다")
    void score_WhenTeamIsNotFivePlayers_ReturnBadRequest() {
        // given
        List<String> participants = tenParticipants();
        participants.set(5, participant(5).replace("\"teamId\": 200", "\"teamId\": 100"));

        // when & then
        given().contentType(ContentType.JSON)
                .body(body(420, 1800, participants))
                .when().post("/api/df-score")
                .then().statusCode(400)
                .body("detail", containsString("5명"));
    }

    @Test
    @DisplayName("채점 대상 경기에서 킬과 어시스트의 합이 팀 킬보다 크면 400으로 응답한다")
    void score_WhenKillsAndAssistsExceedTeamKills_ReturnBadRequest() {
        // given
        List<String> participants = tenParticipants();
        participants.set(0, participant(0).replace("\"assists\": 5", "\"assists\": 13"));

        // when & then
        given().contentType(ContentType.JSON)
                .body(body(420, 1800, participants))
                .when().post("/api/df-score")
                .then().statusCode(400)
                .body("detail", containsString("팀 킬"));
    }

    @Test
    @DisplayName("채점 대상 경기에서 오브젝트 피해가 포탑 피해보다 작으면 400으로 응답한다")
    void score_WhenObjectiveDamageIsLessThanTurretDamage_ReturnBadRequest() {
        // given
        List<String> participants = tenParticipants();
        participants.set(0, participant(0).replace("\"damageDealtToObjectives\": 5000", "\"damageDealtToObjectives\": 2000"));

        // when & then
        given().contentType(ContentType.JSON)
                .body(body(420, 1800, participants))
                .when().post("/api/df-score")
                .then().statusCode(400)
                .body("detail", containsString("오브젝트 피해"));
    }

    @Test
    @DisplayName("포지션이 없는 칼바람 경기는 400이 아니라 점수 없이 응답한다")
    void score_WhenUnsupportedQueueHasNoPosition_ReturnNullForEveryParticipant() {
        // given
        List<String> participants = tenParticipants().stream()
                .map(participant -> participant.replaceAll("\"position\": \"[A-Z]+\"", "\"position\": null"))
                .toList();

        // when & then
        given().contentType(ContentType.JSON)
                .body(body(450, 1800, participants))
                .when().post("/api/df-score")
                .then().statusCode(200)
                .body("participants.score", everyItem(nullValue()));
    }

    @Test
    @DisplayName("채점 대상이 아닌 경기는 팀 구성이 모순돼도 점수 없이 응답한다")
    void score_WhenUnscorableMatchHasDuplicatedPosition_ReturnNullForEveryParticipant() {
        // given
        List<String> participants = tenParticipants();
        participants.set(4, participant(4).replace("\"SUPPORT\"", "\"MID\""));

        // when & then
        given().contentType(ContentType.JSON)
                .body(body(420, 899, participants))
                .when().post("/api/df-score")
                .then().statusCode(200)
                .body("participants.score", everyItem(nullValue()));
    }

    @Test
    @DisplayName("채점 대상 경기의 요청이 올바르면 200으로 응답한다")
    void score_WhenScorableMatchIsValid_ReturnOk() {
        // when & then
        given().contentType(ContentType.JSON)
                .body(body(420, 1800, tenParticipants()))
                .when().post("/api/df-score")
                .then().statusCode(200)
                .body("participants", hasSize(10));
    }

    @Test
    @DisplayName("채점 대상 경기는 10명 모두 점수를 받는다")
    void score_WhenScorableMatch_ReturnScoreForEveryParticipant() {
        // when & then
        given().contentType(ContentType.JSON)
                .body(body(420, 1800, tenParticipants()))
                .when().post("/api/df-score")
                .then().statusCode(200)
                .body("participants.score", everyItem(notNullValue()));
    }

    @Test
    @DisplayName("참조 경기의 점수가 참조 구현과 같고, 이긴 팀 최고점은 MVP·진 팀 최고점은 ACE다")
    void score_WhenReferenceMatch_ReturnScoresAndBadges() {
        // given
        String body = """
                {"queueId": 420, "gameDurationSec": 1800, "participants": [
                 {"puuid": "p1", "teamId": 100, "position": "TOP", "win": true, "kills": 5, "deaths": 3, "assists": 4, "totalDamageDealtToChampions": 25000, "totalDamageTaken": 30000, "goldEarned": 13000, "visionScore": 18, "damageDealtToTurrets": 6000, "damageDealtToObjectives": 7000},
                 {"puuid": "p2", "teamId": 100, "position": "JUNGLE", "win": true, "kills": 6, "deaths": 4, "assists": 10, "totalDamageDealtToChampions": 18000, "totalDamageTaken": 35000, "goldEarned": 12000, "visionScore": 40, "damageDealtToTurrets": 1000, "damageDealtToObjectives": 25000},
                 {"puuid": "p3", "teamId": 100, "position": "MID", "win": true, "kills": 7, "deaths": 2, "assists": 6, "totalDamageDealtToChampions": 30000, "totalDamageTaken": 15000, "goldEarned": 13500, "visionScore": 22, "damageDealtToTurrets": 4000, "damageDealtToObjectives": 5000},
                 {"puuid": "p4", "teamId": 100, "position": "BOTTOM", "win": true, "kills": 8, "deaths": 3, "assists": 5, "totalDamageDealtToChampions": 32000, "totalDamageTaken": 14000, "goldEarned": 14500, "visionScore": 15, "damageDealtToTurrets": 5000, "damageDealtToObjectives": 9000},
                 {"puuid": "p5", "teamId": 100, "position": "SUPPORT", "win": true, "kills": 1, "deaths": 4, "assists": 18, "totalDamageDealtToChampions": 9000, "totalDamageTaken": 16000, "goldEarned": 8500, "visionScore": 80, "damageDealtToTurrets": 500, "damageDealtToObjectives": 800},
                 {"puuid": "p6", "teamId": 200, "position": "TOP", "win": false, "kills": 3, "deaths": 6, "assists": 3, "totalDamageDealtToChampions": 20000, "totalDamageTaken": 32000, "goldEarned": 11000, "visionScore": 15, "damageDealtToTurrets": 3000, "damageDealtToObjectives": 3500},
                 {"puuid": "p7", "teamId": 200, "position": "JUNGLE", "win": false, "kills": 4, "deaths": 7, "assists": 6, "totalDamageDealtToChampions": 15000, "totalDamageTaken": 33000, "goldEarned": 10000, "visionScore": 35, "damageDealtToTurrets": 0, "damageDealtToObjectives": 18000},
                 {"puuid": "p8", "teamId": 200, "position": "MID", "win": false, "kills": 5, "deaths": 6, "assists": 4, "totalDamageDealtToChampions": 24000, "totalDamageTaken": 18000, "goldEarned": 11500, "visionScore": 20, "damageDealtToTurrets": 2000, "damageDealtToObjectives": 2500},
                 {"puuid": "p9", "teamId": 200, "position": "BOTTOM", "win": false, "kills": 4, "deaths": 7, "assists": 3, "totalDamageDealtToChampions": 22000, "totalDamageTaken": 15000, "goldEarned": 11000, "visionScore": 12, "damageDealtToTurrets": 1500, "damageDealtToObjectives": 4000},
                 {"puuid": "p10", "teamId": 200, "position": "SUPPORT", "win": false, "kills": 0, "deaths": 6, "assists": 10, "totalDamageDealtToChampions": 7000, "totalDamageTaken": 17000, "goldEarned": 7000, "visionScore": 70, "damageDealtToTurrets": 0, "damageDealtToObjectives": 300}
                ]}""";

        // when & then
        given().contentType(ContentType.JSON)
                .body(body)
                .when().post("/api/df-score")
                .then().statusCode(200)
                .body("participants.score", contains(66.7f, 66.1f, 74.5f, 66.1f, 67.8f, 47.2f, 49.3f, 54.9f, 40.2f, 46.4f))
                .body("participants.badge", contains(null, null, "MVP", null, null, null, null, "ACE", null, null));
    }

    @Test
    @DisplayName("한 팀 안에서 승패가 엇갈리면 400으로 응답한다")
    void score_WhenWinIsMixedInTeam_ReturnBadRequest() {
        // given
        List<String> participants = tenParticipants();
        participants.set(1, participant(1).replace("\"win\": true", "\"win\": false"));

        // when & then
        given().contentType(ContentType.JSON)
                .body(body(420, 1800, participants))
                .when().post("/api/df-score")
                .then().statusCode(400)
                .body("detail", containsString("승패"));
    }
}
