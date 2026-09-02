package dfgg.presentation;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.match.NormalizedMatchParticipantRepository;
import dfgg.domain.match.RawMatchRepository;
import dfgg.domain.match.RawMatchTimelineRepository;
import io.restassured.RestAssured;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Sql("/sql/renormalization-test-data.sql")
class RenormalizationControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private NormalizedMatchParticipantRepository participantRepository;
    @Autowired
    private RawMatchTimelineRepository timelineRepository;
    @Autowired
    private RawMatchRepository rawMatchRepository;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @AfterEach
    void cleanUp() {
        participantRepository.deleteAllInBatch();
        timelineRepository.deleteAllInBatch();
        rawMatchRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("대상 매치 수와 다음 커서를 응답한다 — 이어서 돌릴 수 있어야 한다")
    void renormalize_WhenCalled_ReportsProgressAndNextCursor() {
        // when: 픽스처의 raw_data가 '{}'라 정규화 자체는 실패하지만,
        //       배치가 대상을 찾아 처리하고 커서를 진행시키는지는 확인할 수 있다
        given()
                .queryParam("tier", "PLATINUM")
                .queryParam("limit", 10)
                .when().post("/admin/riot/matches/renormalize")
                .then().statusCode(200)
                .body("processed", org.hamcrest.Matchers.equalTo(2))
                .body("nextCursor", org.hamcrest.Matchers.equalTo("R2"));
    }

    @Test
    @DisplayName("커서를 주면 그 다음부터 처리한다")
    void renormalize_WhenCursorGiven_StartsAfterIt() {
        given()
                .queryParam("tier", "PLATINUM")
                .queryParam("afterMatchId", "R1")
                .queryParam("limit", 10)
                .when().post("/admin/riot/matches/renormalize")
                .then().statusCode(200)
                .body("processed", org.hamcrest.Matchers.equalTo(1))
                .body("nextCursor", org.hamcrest.Matchers.equalTo("R2"));
    }

    @Test
    @DisplayName("limit이 없으면 거부한다 — 실수로 전량을 돌리지 못하게 한다")
    void renormalize_WhenLimitMissing_ReturnsBadRequest() {
        given()
                .queryParam("tier", "PLATINUM")
                .when().post("/admin/riot/matches/renormalize")
                .then().statusCode(400);
    }

    @Test
    @DisplayName("limit 상한을 넘으면 거부한다")
    void renormalize_WhenLimitTooLarge_ReturnsBadRequest() {
        given()
                .queryParam("tier", "PLATINUM")
                .queryParam("limit", 10000)
                .when().post("/admin/riot/matches/renormalize")
                .then().statusCode(400);
    }

    @Test
    @DisplayName("알 수 없는 티어는 거부한다")
    void renormalize_WhenTierIsInvalid_ReturnsBadRequest() {
        given()
                .queryParam("tier", "CHALLENGER")
                .queryParam("limit", 10)
                .when().post("/admin/riot/matches/renormalize")
                .then().statusCode(400);
    }

    @Test
    @DisplayName("대상이 없으면 처리 0건으로 응답한다")
    void renormalize_WhenNoTargets_ReturnsZeroProcessed() {
        given()
                .queryParam("tier", "DIAMOND")
                .queryParam("limit", 10)
                .when().post("/admin/riot/matches/renormalize")
                .then().statusCode(200)
                .body("processed", org.hamcrest.Matchers.equalTo(0))
                .body("hasMore", org.hamcrest.Matchers.equalTo(false));
    }
}
