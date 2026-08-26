package dfgg.presentation;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.embedding.EmbeddingRepository;
import dfgg.domain.sequence.MinedSequentialPatternRepository;
import dfgg.domain.stats.ChampionBuildStatsRepository;
import dfgg.presentation.dto.ChampionDto;
import dfgg.presentation.dto.request.NextItemRecommendationRequest;
import dfgg.presentation.dto.response.NextItemRecommendationResponse;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.util.List;
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
@Sql("/sql/next-item-recommendation-controller-test-data.sql")
class NextItemRecommendationControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private EmbeddingRepository embeddingRepository;

    @Autowired
    private MinedSequentialPatternRepository minedSequentialPatternRepository;

    @Autowired
    private ChampionBuildStatsRepository championBuildStatsRepository;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        RestAssured.port = port;
        // ①(안전/탐색 구역)과 ④(composition_stats)가 다른 테스트의 잔여 데이터로 우연히
        // 응답을 만들어버리면 ⑤ 폴백까지 내려가는지 검증할 수 없으므로 비워둔다.
        embeddingRepository.deleteAllInBatch();
        minedSequentialPatternRepository.deleteAllInBatch();
        championBuildStatsRepository.deleteAllInBatch();
    }

    private NextItemRecommendationRequest requestOf(List<Long> purchasedItemIds) {
        return new NextItemRecommendationRequest(
                new ChampionDto("Jinx", "BOTTOM"),
                purchasedItemIds,
                List.of(
                        new ChampionDto("AllyChamp", "TOP"),
                        new ChampionDto("AllyChamp", "JUNGLE"),
                        new ChampionDto("AllyChamp", "MID"),
                        new ChampionDto("AllyChamp", "SUPPORT")
                ),
                List.of(
                        new ChampionDto("EnemyChamp", "TOP"),
                        new ChampionDto("EnemyChamp", "JUNGLE"),
                        new ChampionDto("EnemyChamp", "MID"),
                        new ChampionDto("EnemyChamp", "BOTTOM"),
                        new ChampionDto("EnemyChamp", "SUPPORT")
                ),
                "PLATINUM",
                "16.16"
        );
    }

    @Test
    @DisplayName("안전/탐색/통계 구역에 데이터가 없고 아직 아무것도 안 샀으면 최다빈도 빌드의 첫 아이템 하나를 추천한다")
    void recommendNextItem_WhenNoDataInEarlierStagesAndNoPurchase_FallsThroughToFirstItemOfMostFrequentBuild() {
        // given & when
        NextItemRecommendationResponse response = given()
                .contentType(ContentType.JSON)
                .body(requestOf(List.of()))
                .when().post("/api/recommendations/v3")
                .then()
                .statusCode(200)
                .extract().as(NextItemRecommendationResponse.class);

        // then
        assertThat(response.servedBy()).isEqualTo("MOST_FREQUENT_BUILD");
        assertThat(response.recommendedItems()).extracting("name").containsExactly("루난의 허리케인");
    }

    @Test
    @DisplayName("이미 1개를 샀으면 최다빈도 빌드에서 그다음 순번의 아이템 하나를 추천한다")
    void recommendNextItem_WhenOneItemAlreadyPurchased_RecommendsNextItemInMostFrequentBuild() {
        // given & when: 최다빈도 빌드 '3072,3006'에서 3072(루난)를 이미 샀으니 다음은 3006(광전사)
        NextItemRecommendationResponse response = given()
                .contentType(ContentType.JSON)
                .body(requestOf(List.of(3072L)))
                .when().post("/api/recommendations/v3")
                .then()
                .statusCode(200)
                .extract().as(NextItemRecommendationResponse.class);

        // thenㅈ
        assertThat(response.servedBy()).isEqualTo("MOST_FREQUENT_BUILD");
        assertThat(response.recommendedItems()).extracting("name").containsExactly("광전사의 군화");
    }

    @Test
    @DisplayName("어느 폴백 단계도 답을 찾지 못하면 404를 반환한다")
    void recommendNextItem_WhenNoStageCanServe_Returns404() {
        // given: 시딩된 매치 데이터가 없는 챔피언
        NextItemRecommendationRequest request = new NextItemRecommendationRequest(
                new ChampionDto("AllyChamp", "TOP"),
                List.of(),
                List.of(
                        new ChampionDto("EnemyChamp", "TOP"), new ChampionDto("EnemyChamp", "JUNGLE"),
                        new ChampionDto("EnemyChamp", "MID"), new ChampionDto("EnemyChamp", "SUPPORT")
                ),
                List.of(
                        new ChampionDto("Jinx", "TOP"), new ChampionDto("Jinx", "JUNGLE"),
                        new ChampionDto("Jinx", "MID"), new ChampionDto("Jinx", "BOTTOM"),
                        new ChampionDto("Jinx", "SUPPORT")
                ),
                "PLATINUM",
                "16.16"
        );

        // when & then: 기본 설정(server.error.include-message=never)에서는 에러 바디에
        // 예외 메시지가 노출되지 않으므로 상태 코드만 검증한다
        given()
                .contentType(ContentType.JSON)
                .body(request)
                .when().post("/api/recommendations/v3")
                .then()
                .statusCode(404);
    }
}
