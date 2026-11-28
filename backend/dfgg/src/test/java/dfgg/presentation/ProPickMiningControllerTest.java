package dfgg.presentation;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;

import dfgg.application.propick.ProLeagueCollectionResult;
import dfgg.application.propick.ProPickCollectionResult;
import dfgg.application.propick.ProPickCollectionService;
import dfgg.application.propick.ProPickSkipReason;
import io.restassured.RestAssured;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ProPickMiningControllerTest {

    @LocalServerPort
    private int port;

    @MockitoBean
    private ProPickCollectionService collectionService;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    @DisplayName("관리자가 대회 픽 수집을 트리거하면 리그별 받은 행·저장 픽·건너뛴 행·실패 사유를 응답한다")
    void collectProPicks_WhenTriggered_ReportPerLeague() {
        // given
        given(collectionService.collect()).willReturn(new ProPickCollectionResult(List.of(
                ProLeagueCollectionResult.collected("LCK/", 120, 118, Map.of(ProPickSkipReason.UNKNOWN_CHAMPION, 2)),
                ProLeagueCollectionResult.failed("LPL/", "Leaguepedia 재시도 3회를 넘겼습니다: ratelimited"))));

        // when & then
        RestAssured.given()
                .when().post("/admin/mining/pro-picks")
                .then().statusCode(200)
                .body("leagues[0].league", equalTo("LCK/"))
                .body("leagues[0].fetchedRows", equalTo(120))
                .body("leagues[0].savedPicks", equalTo(118))
                .body("leagues[0].skippedRows.UNKNOWN_CHAMPION", equalTo(2))
                .body("leagues[0].failure", nullValue())
                .body("leagues[1].failure", equalTo("Leaguepedia 재시도 3회를 넘겼습니다: ratelimited"));
    }
}
