package dfgg.presentation;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.counter.ChampionPositionStatsRepository;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import io.restassured.RestAssured;
import io.restassured.path.json.JsonPath;
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
class StatsAggregationAllControllerTest {

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
    @DisplayName("전체 집계를 트리거하면 등록된 단계를 순서대로 실행하고 단계별 결과를 응답한다")
    void aggregateAll_WhenTriggered_RunEveryStepInOrder() {
        // when
        JsonPath response = given()
                .when().post("/admin/mining/all")
                .then().statusCode(200)
                .extract().jsonPath();

        // then
        assertThat(response.getList("steps.name")).containsExactly("item-stats", "champion-positions");
        assertThat(response.getString("steps[1].result.patch")).isEqualTo("16.10");
        assertThat(response.getLong("durationMillis")).isNotNegative();
        assertThat(statsRepository.count()).isEqualTo(4);
    }
}
