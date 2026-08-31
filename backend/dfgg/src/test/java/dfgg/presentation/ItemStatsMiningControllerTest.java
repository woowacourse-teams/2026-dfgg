package dfgg.presentation;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import dfgg.application.itemstats.ItemStatsAggregationResult;
import dfgg.domain.itemstats.ChampionItemStatsRepository;
import dfgg.domain.itemstats.ChampionPairItemStatsRepository;
import io.restassured.RestAssured;
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
@Sql("/sql/item-stats-aggregation-test-data.sql")
class ItemStatsMiningControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ChampionItemStatsRepository championItemStatsRepository;

    @Autowired
    private ChampionPairItemStatsRepository championPairItemStatsRepository;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    @DisplayName("관리자가 통계 집계를 트리거하면 네 테이블이 채워지고 최근 패치 윈도를 응답한다")
    void aggregateItemStats_WhenTriggered_PopulatesStatsAndReportsRecentWindow() {
        // when
        ItemStatsAggregationResult result = given()
                .queryParam("recentPatchWindowSize", 1)
                .when().post("/admin/mining/item-stats")
                .then().statusCode(200)
                .extract().as(ItemStatsAggregationResult.class);

        // then
        assertThat(result.recentPatches()).containsExactly("16.17");
        assertThat(result.championItemStatsCount()).isPositive();
        assertThat(result.championItemRollupCount()).isPositive();
        assertThat(result.championPairItemStatsCount()).isPositive();
        assertThat(result.itemMetaStatsCount()).isPositive();
        assertThat(championItemStatsRepository.count()).isEqualTo(result.championItemStatsCount());
        assertThat(championPairItemStatsRepository.count()).isEqualTo(result.championPairItemStatsCount());
    }

    @Test
    @DisplayName("윈도 크기를 주지 않으면 기본값 3을 쓴다")
    void aggregateItemStats_WhenWindowSizeOmitted_UsesDefaultOfThree() {
        // when: 픽스처 패치는 16.15/16.17 둘뿐이라 윈도 3이면 전부 최근이 된다
        ItemStatsAggregationResult result = given()
                .when().post("/admin/mining/item-stats")
                .then().statusCode(200)
                .extract().as(ItemStatsAggregationResult.class);

        // then
        assertThat(result.recentPatches()).containsExactlyInAnyOrder("16.15", "16.17");
    }

    @Test
    @DisplayName("윈도 크기가 1 미만이면 400을 반환한다")
    void aggregateItemStats_WhenWindowSizeIsNotPositive_ReturnsBadRequest() {
        given()
                .queryParam("recentPatchWindowSize", 0)
                .when().post("/admin/mining/item-stats")
                .then().statusCode(400);
    }
}
