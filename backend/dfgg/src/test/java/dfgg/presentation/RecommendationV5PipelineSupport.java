package dfgg.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.application.recommend.component.ComponentRecommendationRuntime;
import dfgg.application.itemstats.ItemStatsAggregationService;
import dfgg.domain.champion.ChampionRepository;
import dfgg.domain.item.ItemRepository;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import dfgg.domain.itemstats.ChampionItemRollupRepository;
import dfgg.domain.itemstats.ChampionItemStatsRepository;
import dfgg.domain.itemstats.ChampionPairItemStatsRepository;
import dfgg.domain.itemstats.ItemMetaStatsRepository;
import dfgg.infrastructure.external.dto.ItemResponse;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 새 추천 API를 Core 추천까지 실제로 돌려 보는 테스트의 공통 준비.
 *
 * <p>Core 파이프라인(후보 생성, 필터, feature, 랭커)은 바꿔 끼우지 않는다. 테스트 DB에 기존 v3 파이프라인 테스트의 표본을 넣고 집계한다.
 * 조합 추천만 바꿔 끼운다. 테스트 환경에는 조합 모델이 없어 그대로 두면 v4·v5가 503이다.
 *
 * <p>요청은 야스오(MID)가 몰락한 왕의 검과 무한의 대검을 산 뒤다. 표본에서 그다음은 도미닉 경의 인사(3판)와 광전사의 군화(2판)다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql("/sql/v3-recommendation-test-data.sql")
abstract class RecommendationV5PipelineSupport {

    protected static final String V3 = "/api/recommendations/v3";
    protected static final String V4 = "/api/recommendations/v4";
    protected static final String V5 = "/api/recommendations/v5";
    protected static final ObjectMapper JSON = new ObjectMapper();

    private static final List<String> ALLIES = List.of("징크스:BOTTOM", "쓰레쉬:SUPPORT", "리신:JUNGLE", "오른:TOP");
    private static final List<String> ENEMIES =
            List.of("람머스:TOP", "아리:MID", "케이틀린:BOTTOM", "레오나:SUPPORT", "엘리스:JUNGLE");

    @Autowired
    protected MockMvc mvc;

    @MockitoBean
    private ComponentRecommendationRuntime components;

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
    void prepareStatsAndComponents() throws Exception {
        aggregationService.aggregate(3);
        ItemResponse catalog = JSON.readValue("""
                {"version":"99.1","dataVersion":"99.1.1","data":{}}
                """, ItemResponse.class);
        when(components.isAvailable()).thenReturn(true);
        when(components.catalog()).thenReturn(catalog);
        when(components.recommend(any())).thenReturn(List.of());
    }

    /** {@code @SpringBootTest}는 롤백하지 않는다. 넣은 표본과 집계 결과를 지워 다른 테스트에 남기지 않는다. */
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

    protected String v3Body() {
        return """
                {"myChampion":{"name":"야스오","position":"MID"},"purchasedItemIds":[6673,3031],
                 "allies":[%s],"enemies":[%s],"tier":"EMERALD","patch":"16.17"}
                """.formatted(champions(ALLIES), champions(ENEMIES));
    }

    protected String v4Body() {
        return """
                {"myChampion":{"name":"야스오","position":"MID"},"purchasedItemIds":[6673,3031],
                 "allies":[%s],"enemies":[%s],"tier":"EMERALD","patch":"16.17",
                 "inventoryItems":[],"availableGold":1000,"gameTimeMs":600000}
                """.formatted(champions(ALLIES), champions(ENEMIES));
    }

    /** v4 요청과 같은 10명에 상태를 더한다. 상대 다섯 명의 킬 수만 바꿔 가며 부른다. */
    protected String v5Body(int killsOfEachEnemy) {
        return """
                {"myChampion":{"name":"야스오","position":"MID","level":9,"kills":2,"deaths":1,"assists":3},
                 "purchasedItemIds":[6673,3031],
                 "allies":[%s],"enemies":[%s],"tier":"EMERALD","patch":"16.17",
                 "inventoryItems":[],"availableGold":1000,"gameTimeMs":600000}
                """.formatted(players(ALLIES, 1), players(ENEMIES, killsOfEachEnemy));
    }

    protected List<Long> coreItemIds(String endpoint, String body) throws Exception {
        String response = mvc.perform(post(endpoint).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Long> itemIds = new ArrayList<>();
        for (JsonNode item : JSON.readTree(response).get("recommendedItems")) {
            itemIds.add(item.get("id").asLong());
        }
        return itemIds;
    }

    private String champions(List<String> players) {
        return players.stream()
                .map(player -> player.split(":"))
                .map(player -> "{\"name\":\"%s\",\"position\":\"%s\"}".formatted(player[0], player[1]))
                .reduce((left, right) -> left + "," + right)
                .orElseThrow();
    }

    private String players(List<String> players, int kills) {
        return players.stream()
                .map(player -> player.split(":"))
                .map(player -> "{\"name\":\"%s\",\"position\":\"%s\",\"level\":9,\"kills\":%d,\"assists\":1,\"itemIds\":[]}"
                        .formatted(player[0], player[1], kills))
                .reduce((left, right) -> left + "," + right)
                .orElseThrow();
    }
}
