package dfgg.presentation;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import dfgg.application.counter.ChampionPositionAggregationService;
import io.restassured.RestAssured;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class StatsAggregationAllFailureTest {

    @LocalServerPort
    private int port;

    @MockitoBean
    private ChampionPositionAggregationService championPositionAggregationService;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    @DisplayName("한 단계가 실패하면 500과 함께 실패한 단계와 끝난 단계를 알린다")
    void aggregateAll_WhenStepFails_RespondFailedStep() {
        // given
        when(championPositionAggregationService.aggregate()).thenThrow(new IllegalStateException("db down"));

        // when
        JsonPath response = given()
                .when().post("/admin/mining/all")
                .then().statusCode(500)
                .extract().jsonPath();

        // then
        assertThat(response.getString("failedStep")).isEqualTo("champion-positions");
        assertThat(response.getList("completedSteps")).containsExactly("item-stats");
        assertThat(response.getString("detail")).doesNotContain("db down");
    }
}
