package dfgg.presentation;

import static io.restassured.RestAssured.given;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import dfgg.application.itemstats.ItemStatsAggregationService;
import dfgg.domain.champion.ChampionRepository;
import dfgg.domain.item.ItemRepository;
import dfgg.domain.itemstats.ChampionItemRollupRepository;
import dfgg.domain.itemstats.ChampionItemStatsRepository;
import dfgg.domain.itemstats.ChampionPairItemStatsRepository;
import dfgg.domain.itemstats.ItemMetaStatsRepository;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import dfgg.presentation.dto.ChampionDto;
import dfgg.presentation.dto.request.NextItemRecommendationRequest;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.jdbc.Sql;

/**
 * 최근 패치 목록은 요청마다 다시 계산하지 않는다.
 * <p>
 * 참가자 테이블 전체를 훑는 쿼리라, 요청마다 돌면 RDS CPU를 포화시킨다(부하 테스트: DB 쿼리 실행 시간의 77~80%, 2 RPS에서 CPU 98%).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Sql("/sql/v3-recommendation-test-data.sql")
class RecentPatchWindowLookupTest {

    @LocalServerPort
    private int port;

    @MockitoSpyBean
    private NormalizedMatchParticipantRepository participantRepository;

    @Autowired
    private ItemStatsAggregationService aggregationService;
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

    // @SpringBootTest는 롤백하지 않는다. 남기면 다른 테스트의 전체 개수 검증이 깨진다.
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

    @Test
    @DisplayName("추천을 여러 번 받아도 최근 패치 목록은 DB에서 한 번만 계산한다")
    void recommendV3_WhenRequestedRepeatedly_LoadRecentPatchesOnlyOnce() {
        // given: 집계도 최근 패치 목록을 읽는다. 추천 요청의 호출만 세도록 지운다
        clearInvocations(participantRepository);
        NextItemRecommendationRequest request = yasuoMidRequest();

        // when
        recommend(request);
        recommend(request);
        recommend(request);

        // then
        verify(participantRepository, times(1)).findDistinctPatches();
    }

    private void recommend(NextItemRecommendationRequest request) {
        given().contentType(ContentType.JSON).body(request)
                .when().post("/api/recommendations/v3")
                .then().statusCode(200);
    }

    private NextItemRecommendationRequest yasuoMidRequest() {
        return new NextItemRecommendationRequest(
                new ChampionDto("야스오", "MID"),
                List.of(6673L),
                List.of(new ChampionDto("징크스", "BOTTOM"), new ChampionDto("쓰레쉬", "SUPPORT"),
                        new ChampionDto("리신", "JUNGLE"), new ChampionDto("오른", "TOP")),
                List.of(new ChampionDto("람머스", "TOP"), new ChampionDto("아리", "MID"),
                        new ChampionDto("케이틀린", "BOTTOM"), new ChampionDto("레오나", "SUPPORT"),
                        new ChampionDto("엘리스", "JUNGLE")),
                "EMERALD", "16.17"
        );
    }
}
