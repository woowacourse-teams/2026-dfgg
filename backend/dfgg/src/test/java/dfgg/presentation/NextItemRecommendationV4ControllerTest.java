package dfgg.presentation;

import static dfgg.application.recommend.context.TestCompositions.NO_TAGS;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.application.champion.ChampionService;
import dfgg.application.recommend.NextItemRecommendationService;
import dfgg.application.recommend.component.ComponentRecommendationRuntime;
import dfgg.application.recommend.component.ComponentRecommendationService;
import dfgg.application.recommend.context.CompositionFeatureCalculator;
import dfgg.application.recommend.v3.ranker.DecisionTree;
import dfgg.application.recommend.v3.ranker.GradientBoostedTrees;
import dfgg.common.exception.NextItemRecommendationNotFoundException;
import dfgg.domain.champion.Champion;
import dfgg.infrastructure.external.dto.ItemResponse;
import dfgg.presentation.dto.RecommendationDescription;
import dfgg.presentation.dto.RecommendedItemDto;
import dfgg.presentation.dto.response.NextItemRecommendationResponse;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** HTTP 바인딩부터 실제 v4 서비스·Component 계산·JSON 응답까지 연결한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NextItemRecommendationV4ControllerTest {
    private static final String ENDPOINT = "/api/recommendations/v4";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired MockMvc mvc;
    @MockitoBean NextItemRecommendationService core;
    @MockitoBean ComponentRecommendationRuntime runtime;
    @MockitoBean ChampionService champions;
    @MockitoBean CompositionFeatureCalculator compositions;

    @BeforeEach
    void setUp() throws Exception {
        var catalog = JSON.readValue("""
                {"version":"99.1","dataVersion":"99.1.1","data":{
                  "1036":{"name":"롱소드","gold":{"total":350,"purchasable":true}},
                  "3133":{"name":"콜필드의 전투 망치","from":["1036","1036"],
                           "gold":{"total":1100,"purchasable":true}}
                }}
                """, ItemResponse.class);
        var model = new GradientBoostedTrees(List.of(new DecisionTree(new int[]{5}, new double[]{0.5},
                new boolean[]{true}, new int[]{-1}, new int[]{-2}, new double[]{0, 10},
                new double[]{2}, new double[]{1, 1})));
        var component = new ComponentRecommendationService(model);
        when(runtime.isAvailable()).thenReturn(true);
        when(runtime.catalog()).thenReturn(catalog);
        when(runtime.recommend(any())).thenAnswer(invocation -> component.recommend(invocation.getArgument(0), catalog));
        when(core.recommendNextItem(any())).thenReturn(new NextItemRecommendationResponse(List.of(
                coreItem(3133), coreItem(9999))));
        when(compositions.calculate(anyList(), anyList())).thenReturn(NO_TAGS);
        for (int id = 1; id <= 10; id++) {
            Champion champion = mock(Champion.class);
            when(champion.getChampionId()).thenReturn((long) id);
            when(champions.findChampionByName("champion" + id)).thenReturn(champion);
        }
    }

    @Test
    void recommendV4_returnsCoreAndTopPurchaseBundle() throws Exception {
        mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.recommendedItems.length()").value(2))
                .andExpect(jsonPath("$.recommendedItems[0].id").value(3133))
                .andExpect(jsonPath("$.recommendedItems[0].name").value("Core 3133"))
                .andExpect(jsonPath("$.recommendedItems[0].imageUrl").value("core-image-3133"))
                .andExpect(jsonPath("$.recommendedItems[0].description.traits[0]").value("공격력"))
                .andExpect(jsonPath("$.recommendedItems[0].purchaseBundle.items.length()").value(1))
                .andExpect(jsonPath("$.recommendedItems[0].purchaseBundle.items[0].itemId").value(3133))
                .andExpect(jsonPath("$.recommendedItems[0].purchaseBundle.items[0].name").value("콜필드의 전투 망치"))
                .andExpect(jsonPath("$.recommendedItems[0].purchaseBundle.items[0].quantity").value(1))
                .andExpect(jsonPath("$.recommendedItems[0].purchaseBundle.items[0].imageUrl")
                        .value("https://test-bucket.s3.ap-northeast-2.amazonaws.com/dfgg/images/99.1/items/3133.png"))
                .andExpect(jsonPath("$.recommendedItems[0].purchaseBundle.additionalCost").value(750))
                .andExpect(jsonPath("$.recommendedItems[0].purchaseBundle.purchaseOrder[0]").value(3133))
                .andExpect(jsonPath("$.recommendedItems[1].id").value(9999))
                .andExpect(jsonPath("$.recommendedItems[1]", hasKey("purchaseBundle")))
                .andExpect(jsonPath("$.recommendedItems[1].purchaseBundle", nullValue()))
                .andExpect(jsonPath("$.recommendedItems[1]", not(hasKey("reason"))))
                .andExpect(jsonPath("$.recommendedItems[1]", not(hasKey("status"))));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            availableGold | null | availableGold
            availableGold | -1 | availableGold
            gameTimeMs | null | gameTimeMs
            gameTimeMs | -1 | gameTimeMs
            inventoryItems | null | inventoryItems
            inventoryItems | [{"itemId":1036,"quantity":0}] | quantity
            inventoryItems | [{"itemId":null,"quantity":1}] | itemId
            inventoryItems | [{"itemId":2147483648,"quantity":1}] | itemId
            inventoryItems | [null] | inventoryItems
            inventoryItems | [{"itemId":1036,"quantity":1},{"itemId":1036,"quantity":1}] | inventoryItemIdsUnique
            purchasedItemIds | [null] | purchasedItemIds
            purchasedItemIds | [1,2,3,4,5,6,7] | purchasedItemCountWithinLimit
            allies | [null,null,null,null] | allies
            enemies | [] | enemies
            myChampion | {"name":"champion10","position":"INVALID"} | position
            """)
    void 잘못된_입력은_서비스_호출_전에_400으로_거부한다(String field, String value, String detail) throws Exception {
        var request = JSON.readTree(body());
        ((com.fasterxml.jackson.databind.node.ObjectNode) request).set(field, JSON.readTree(value));
        mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail", containsString(detail)));
        verifyNoInteractions(core, runtime, champions, compositions);
    }

    @Test
    void 운영_패치_불일치는_400이다() throws Exception {
        mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                        .content(body().replace("\"patch\":null", "\"patch\":\"99.0\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("현재 운영 패치")));
        verifyNoInteractions(core, runtime, champions, compositions);
    }

    @Test
    void 카탈로그에_없는_보유_아이템은_400이다() throws Exception {
        mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                        .content(body().replace("\"itemId\":1036", "\"itemId\":8888")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("8888")));
        verifyNoInteractions(core, champions, compositions);
    }

    @Test
    void Component가_준비되지_않았으면_503이고_v3는_계속_응답한다() throws Exception {
        when(runtime.isAvailable()).thenReturn(false);
        mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.detail").value("현재 Component 추천을 사용할 수 없습니다."))
                .andExpect(jsonPath("$", not(hasKey("stackTrace"))));
        verifyNoInteractions(core);
        mvc.perform(post("/api/recommendations/v3").contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendedItems[0].id").value(3133))
                .andExpect(jsonPath("$.recommendedItems[0]", not(hasKey("purchaseBundle"))));
    }

    @Test
    void Core_추천이_없으면_404이다() throws Exception {
        when(core.recommendNextItem(any())).thenThrow(new NextItemRecommendationNotFoundException("champion10", "TOP"));
        mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void Core가_빈_결과이면_200과_빈_목록을_반환한다() throws Exception {
        when(core.recommendNextItem(any())).thenReturn(new NextItemRecommendationResponse(List.of()));
        mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendedItems").isEmpty());
    }

    @Test
    void 잘못된_JSON은_400이다() throws Exception {
        mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(core, runtime, champions, compositions);
    }

    private RecommendedItemDto coreItem(long id) {
        return new RecommendedItemDto(id, "Core " + id, "core-image-" + id,
                new RecommendationDescription(List.of(), List.of(), List.of("공격력")));
    }

    private String body() throws Exception {
        var allies = IntStream.rangeClosed(1, 4).mapToObj(id -> Map.of("name", "champion" + id, "position", "TOP")).toList();
        var enemies = IntStream.rangeClosed(5, 9).mapToObj(id -> Map.of("name", "champion" + id, "position", "TOP")).toList();
        return """
                {"myChampion":{"name":"champion10","position":"TOP"},"purchasedItemIds":[],
                 "allies":%s,"enemies":%s,"tier":"EMERALD","patch":null,
                 "inventoryItems":[{"itemId":1036,"quantity":1}],"availableGold":1000,"gameTimeMs":600000}
                """.formatted(JSON.writeValueAsString(allies), JSON.writeValueAsString(enemies));
    }
}
