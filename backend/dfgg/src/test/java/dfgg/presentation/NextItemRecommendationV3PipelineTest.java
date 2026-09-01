package dfgg.presentation;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import dfgg.application.itemstats.ItemStatsAggregationService;
import dfgg.application.recommend.v3.ranker.BestSourceScoreRanker;
import dfgg.presentation.dto.ChampionDto;
import dfgg.presentation.dto.request.NextItemRecommendationRequest;
import dfgg.presentation.dto.response.NextItemRecommendationResponse;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.util.List;
import dfgg.domain.champion.ChampionRepository;
import dfgg.domain.item.ItemRepository;
import dfgg.domain.itemstats.ChampionItemRollupRepository;
import dfgg.domain.itemstats.ChampionItemStatsRepository;
import dfgg.domain.itemstats.ChampionPairItemStatsRepository;
import dfgg.domain.itemstats.ItemMetaStatsRepository;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
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
@Sql("/sql/v3-recommendation-test-data.sql")
class NextItemRecommendationV3PipelineTest {

    private static final long KRAKEN = 6673L;
    private static final long INFINITY_EDGE = 3031L;
    private static final long DOMINIK = 3036L;
    private static final long BERSERKERS_GREAVES = 3006L;
    private static final long PLATED_STEELCAPS = 3047L;

    @LocalServerPort
    private int port;

    @Autowired
    private ItemStatsAggregationService aggregationService;

    @Autowired
    private NormalizedMatchParticipantRepository participantRepository;
    @Autowired
    private ChampionRepository championRepository;
    @Autowired
    private ItemRepository itemRepository;
    @Autowired
    private ChampionItemStatsRepository championItemStatsRepository;
    @Autowired
    private ChampionItemRollupRepository championItemRollupRepository;
    @Autowired
    private ChampionPairItemStatsRepository championPairItemStatsRepository;
    @Autowired
    private ItemMetaStatsRepository itemMetaStatsRepository;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        aggregationService.aggregate(3);
    }

    /**
     * {@code @SpringBootTest}는 롤백하지 않는다. {@code @Sql}로 넣은 챔피언·아이템과 집계 결과가
     * 커밋된 채 남으면 전체 아이템 수를 세는 다른 테스트가 깨진다(실제로 깨졌다).
     */
    @AfterEach
    void cleanUp() {
        championItemStatsRepository.deleteAllInBatch();
        championItemRollupRepository.deleteAllInBatch();
        championPairItemStatsRepository.deleteAllInBatch();
        itemMetaStatsRepository.deleteAllInBatch();
        participantRepository.deleteAllInBatch();
        championRepository.deleteAllInBatch();
        itemRepository.deleteAllInBatch();
    }

    private NextItemRecommendationRequest requestWith(List<Long> purchasedItemIds) {
        return new NextItemRecommendationRequest(
                new ChampionDto("야스오", "MID"),
                purchasedItemIds,
                List.of(new ChampionDto("징크스", "BOTTOM"), new ChampionDto("쓰레쉬", "SUPPORT"),
                        new ChampionDto("리신", "JUNGLE"), new ChampionDto("오른", "TOP")),
                List.of(new ChampionDto("람머스", "TOP"), new ChampionDto("아리", "MID"),
                        new ChampionDto("케이틀린", "BOTTOM"), new ChampionDto("레오나", "SUPPORT"),
                        new ChampionDto("엘리스", "JUNGLE")),
                "EMERALD", "16.17"
        );
    }

    private NextItemRecommendationResponse recommend(List<Long> purchasedItemIds) {
        return given().contentType(ContentType.JSON).body(requestWith(purchasedItemIds))
                .when().post("/api/recommendations/v3")
                .then().statusCode(200)
                .extract().as(NextItemRecommendationResponse.class);
    }

    private List<Long> itemIdsOf(NextItemRecommendationResponse response) {
        return response.recommendedItems().stream().map(item -> item.id()).toList();
    }

    @Test
    @DisplayName("새 파이프라인으로 다음 아이템을 추천한다 — 실제 전개에서 온 후보가 나온다")
    void recommendV3_WhenPurchasePrefixMatches_ReturnsItemsFromRealTransitions() {
        // when
        NextItemRecommendationResponse response = recommend(List.of(KRAKEN, INFINITY_EDGE));

        // then
        assertThat(itemIdsOf(response)).contains(DOMINIK);
    }

    @Test
    @DisplayName("Top-5를 넘지 않는다")
    void recommendV3_WhenManyCandidates_ReturnsAtMostFive() {
        // when
        NextItemRecommendationResponse response = recommend(List.of());

        // then
        assertThat(response.recommendedItems()).hasSizeLessThanOrEqualTo(5);
    }

    @Test
    @DisplayName("이미 산 아이템은 추천에 나오지 않는다")
    void recommendV3_WhenItemAlreadyPurchased_NeverRecommendsIt() {
        // when
        NextItemRecommendationResponse response = recommend(List.of(KRAKEN, INFINITY_EDGE));

        // then
        assertThat(itemIdsOf(response)).doesNotContain(KRAKEN, INFINITY_EDGE);
    }

    @Test
    @DisplayName("신발을 이미 신고 있으면 다른 신발을 추천하지 않는다")
    void recommendV3_WhenBootsAlreadyOwned_NeverRecommendsOtherBoots() {
        // when: 광전사의 군화를 신은 상태
        NextItemRecommendationResponse response = recommend(List.of(BERSERKERS_GREAVES));

        // then
        assertThat(itemIdsOf(response)).doesNotContain(PLATED_STEELCAPS, BERSERKERS_GREAVES);
    }

    @Test
    @DisplayName("어떤 후보도 못 찾으면 404를 반환한다 — 기존 v3 계약을 유지한다")
    void recommendV3_WhenNoCandidateFound_ReturnsNotFound() {
        // given: 표본에 전혀 없는 아이템만 들고 있고, 해당 챔피언 통계도 비운다
        given().contentType(ContentType.JSON)
                .body(new NextItemRecommendationRequest(
                        new ChampionDto("람머스", "TOP"), List.of(),
                        List.of(new ChampionDto("징크스", "BOTTOM"), new ChampionDto("쓰레쉬", "SUPPORT"),
                                new ChampionDto("리신", "JUNGLE"), new ChampionDto("오른", "TOP")),
                        List.of(new ChampionDto("야스오", "MID"), new ChampionDto("아리", "MID"),
                                new ChampionDto("케이틀린", "BOTTOM"), new ChampionDto("레오나", "SUPPORT"),
                                new ChampionDto("엘리스", "JUNGLE")),
                        "EMERALD", "16.17"))
                .when().post("/api/recommendations/v3")
                .then().statusCode(404);
    }

    @Test
    @DisplayName("폴백 체인이 아니라 랭커가 순위를 정했음을 응답에 남긴다")
    void recommendV3_WhenServed_ReportsRankerVersionNotFallbackStage() {
        // when
        NextItemRecommendationResponse response = recommend(List.of(KRAKEN, INFINITY_EDGE));

        // then: 구 파이프라인은 FallbackStage 이름(PRIMARY 등)을 돌려줬다
        assertThat(response.servedBy())
                .isEqualTo(BestSourceScoreRanker.MODEL_VERSION)
                .doesNotContain("PRIMARY", "COMPOSITION_STATS", "MOST_FREQUENT_BUILD");
    }
}
