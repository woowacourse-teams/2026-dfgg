package dfgg.presentation;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import dfgg.application.itemstats.ItemStatsAggregationService;
import dfgg.domain.item.trait.ItemTrait;
import dfgg.presentation.dto.ChampionRefDto;
import dfgg.presentation.dto.RecommendedItemDto;
import java.util.Arrays;
import dfgg.application.recommend.v3.ranker.CandidateRanker;
import dfgg.application.recommend.v3.ranker.LambdaMartRanker;
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

    private static final long RAMMUS_ID = 33L;
    private static final long AHRI_ID = 103L;
    private static final long CAITLYN_ID = 51L;
    private static final long LEONA_ID = 89L;
    private static final long ELISE_ID = 60L;

    @LocalServerPort
    private int port;

    @Autowired
    private CandidateRanker candidateRanker;

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
    @DisplayName("응답에 서빙한 모델 정보가 실리지 않는다 — 클라이언트가 쓸 정보가 아니다")
    void recommendV3_WhenServed_CarriesNoServingModelInfo() {
        // when
        String body = given().contentType(ContentType.JSON)
                .body(requestWith(List.of(KRAKEN, INFINITY_EDGE)))
                .when().post("/api/recommendations/v3")
                .then().statusCode(200)
                .extract().asString();

        // then
        assertThat(body).doesNotContain("servedBy");
    }

    @Test
    @DisplayName("추천마다 구조화된 이유를 낸다 — 문장이 아니라 클라이언트가 렌더링할 단어다")
    void recommendV3_IncludesStructuredDescription() {
        NextItemRecommendationResponse response = recommend(List.of(KRAKEN, INFINITY_EDGE));

        assertThat(response.recommendedItems()).allSatisfy(item -> {
            assertThat(item.description()).isNotNull();
            assertThat(item.description().counter()).isNotNull();
            assertThat(item.description().ally()).isNotNull();
            assertThat(item.description().traits()).isNotNull();
        });
    }

    @Test
    @DisplayName("counter로 지목한 챔피언이 질의에 있던 적이다 — 엉뚱한 챔피언을 지목하지 않는다")
    void recommendV3_CounterNamesOnlyEnemiesFromTheQuery() {
        List<Long> enemyIds = List.of(RAMMUS_ID, AHRI_ID, CAITLYN_ID, LEONA_ID, ELISE_ID);

        NextItemRecommendationResponse response = recommend(List.of(KRAKEN, INFINITY_EDGE));

        assertThat(response.recommendedItems())
                .flatMap(RecommendedItemDto::description)
                .isNotNull();
        response.recommendedItems().forEach(item ->
                assertThat(item.description().counter())
                        .extracting(ChampionRefDto::id)
                        .isSubsetOf(enemyIds));
    }

    @Test
    @DisplayName("지목한 챔피언에 한글 이름이 붙는다 — id만으로는 화면에 못 쓴다")
    void recommendV3_CounterCarriesKoreanNames() {
        NextItemRecommendationResponse response = recommend(List.of(KRAKEN, INFINITY_EDGE));

        assertThat(response.recommendedItems())
                .flatMap(item -> item.description().counter())
                .allSatisfy(champion -> assertThat(champion.name()).isNotBlank());
    }

    @Test
    @DisplayName("counter는 두 명을 넘지 않는다")
    void recommendV3_CounterIsCappedAtTwo() {
        NextItemRecommendationResponse response = recommend(List.of(KRAKEN, INFINITY_EDGE));

        assertThat(response.recommendedItems()).allSatisfy(item ->
                assertThat(item.description().counter()).hasSizeLessThanOrEqualTo(2));
    }

    @Test
    @DisplayName("traits는 팀 어휘의 표시명만 낸다 — Data Dragon 원본 태그도 enum 이름도 새어나가지 않는다")
    void recommendV3_TraitsUseTheTeamVocabularyOnly() {
        List<String> vocabulary = Arrays.stream(ItemTrait.values()).map(ItemTrait::getDisplayName).toList();

        NextItemRecommendationResponse response = recommend(List.of(KRAKEN, INFINITY_EDGE));

        assertThat(response.recommendedItems())
                .flatMap(item -> item.description().traits())
                .isSubsetOf(vocabulary);
    }

    @Test
    @DisplayName("응답에 문장이 남아 있지 않다 — 클라이언트가 단어를 조립한다")
    void recommendV3_NoLongerReturnsASentence() {
        String body = given().contentType(ContentType.JSON)
                .body(requestWith(List.of(KRAKEN, INFINITY_EDGE)))
                .when().post("/api/recommendations/v3")
                .then().statusCode(200)
                .extract().asString();

        assertThat(body).doesNotContain("추천했어요");
    }

    @Test
    @DisplayName("응답 본문에 NaN 리터럴이 실려나가지 않는다 — 표준 JSON이 아니라 클라이언트가 깨진다")
    void recommendV3_RawBodyContainsNoNaNLiteral() {
        // 역직렬화 성공은 증거가 못 된다. Jackson은 설정에 따라 NaN 리터럴을 읽어버린다.
        String body = given().contentType(ContentType.JSON)
                .body(requestWith(List.of(KRAKEN, INFINITY_EDGE)))
                .when().post("/api/recommendations/v3")
                .then().statusCode(200)
                .extract().asString();

        assertThat(body).doesNotContain("NaN", "Infinity");
    }

    @Test
    @DisplayName("응답에 SHAP 기여도가 실리지 않는다 — '예측을 얼마나 밀었나'는 사용자에게 보일 이유가 아니다")
    void recommendV3_WhenServed_CarriesNoShapContributions() {
        // when
        String body = given().contentType(ContentType.JSON)
                .body(requestWith(List.of(KRAKEN, INFINITY_EDGE)))
                .when().post("/api/recommendations/v3")
                .then().statusCode(200)
                .extract().asString();

        // then: SHAP은 서버 DEBUG 로그로만 남는다(NextItemRecommendationServiceTest)
        assertThat(body).doesNotContain("reasons", "contributions", "baseValue");
    }

    @Test
    @DisplayName("v3의 유일한 랭커가 LambdaMART다 — 임시 랭커가 남아 있으면 빈이 둘이 되어 여기서 걸린다")
    void candidateRankerBean_IsLambdaMart() {
        assertThat(candidateRanker).isInstanceOf(LambdaMartRanker.class);
    }

    @Test
    @DisplayName("기동 시 모델이 실제로 로드된다 — 스키마가 어긋난 모델이면 컨텍스트가 뜨지 않는다")
    void applicationContext_LoadsTheCommittedModelAtStartup() {
        // 이 테스트가 도는 것 자체가 컨텍스트 기동에 성공했다는 뜻이고,
        // 모델 빈은 기동 시점에 지문·feature 순서를 검증한다.
        assertThat(candidateRanker.modelVersion()).isNotBlank();
    }
}
