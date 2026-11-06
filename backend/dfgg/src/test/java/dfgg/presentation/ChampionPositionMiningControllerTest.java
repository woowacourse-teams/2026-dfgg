package dfgg.presentation;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import dfgg.application.counter.ChampionPositionAggregationResult;
import dfgg.domain.counter.ChampionPositionStatsRepository;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
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
@Sql("/sql/champion-position-test-data.sql")
class ChampionPositionMiningControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ChampionPositionStatsRepository statsRepository;

    @Autowired
    private NormalizedMatchParticipantRepository participantRepository;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @AfterEach
    void cleanUp() {
        statsRepository.deleteAllInBatch();
        participantRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("관리자가 라인 분포 집계를 트리거하면 현재 패치와 저장 행 수를 응답한다")
    void aggregateChampionPositions_WhenTriggered_ReportPatchAndSavedRows() {
        // when
        ChampionPositionAggregationResult result = given()
                .when().post("/admin/mining/champion-positions")
                .then().statusCode(200)
                .extract().as(ChampionPositionAggregationResult.class);

        // then
        assertThat(result.patch()).isEqualTo("16.10");
        assertThat(result.savedRows()).isEqualTo(4);
        assertThat(statsRepository.count()).isEqualTo(4);
    }
}
