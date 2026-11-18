package dfgg.presentation;

import static dfgg.application.recommend.context.TestCompositions.NO_TAGS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dfgg.application.champion.ChampionService;
import dfgg.application.recommend.NextItemRecommendationService;
import dfgg.application.recommend.component.ComponentRecommendationRuntime;
import dfgg.application.recommend.component.ComponentRecommendationService;
import dfgg.application.recommend.context.CompositionFeatureCalculator;
import dfgg.application.recommend.v3.MyState;
import dfgg.application.recommend.v3.ReportedGameState;
import dfgg.application.recommend.v3.ranker.DecisionTree;
import dfgg.application.recommend.v3.ranker.GradientBoostedTrees;
import dfgg.common.exception.NextItemRecommendationNotFoundException;
import dfgg.domain.champion.Champion;
import dfgg.domain.language.Language;
import dfgg.infrastructure.external.dto.ItemResponse;
import dfgg.presentation.dto.ChampionDto;
import dfgg.presentation.dto.RecommendationDescription;
import dfgg.presentation.dto.RecommendedItemDto;
import dfgg.presentation.dto.request.NextItemRecommendationRequest;
import dfgg.presentation.dto.response.NextItemRecommendationResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 새 추천 API. HTTP 요청이 검증을 거쳐 v4와 같은 흐름에 상태와 함께 들어가고, v4와 같은 모양으로 응답한다.
 *
 * <p>Core 추천(v3 서비스)은 바꿔 끼운다. 상태가 상황에 실리고 새 모델이 순위를 매기는 것은 서비스 테스트가 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NextItemRecommendationV5ControllerTest {
    private static final String ENDPOINT = "/api/recommendations/v5";
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
        when(core.recommendNextItem(any(), any(ReportedGameState.class), any()))
                .thenReturn(new NextItemRecommendationResponse(List.of(coreItem(3133), coreItem(9999))));
        when(compositions.calculate(anyList(), anyList())).thenReturn(NO_TAGS);
        for (int id = 1; id <= 10; id++) {
            Champion champion = mock(Champion.class);
            when(champion.getChampionId()).thenReturn((long) id);
            when(champions.findChampionByName("champion" + id)).thenReturn(champion);
        }
    }

    private RecommendedItemDto coreItem(long id) {
        return new RecommendedItemDto(id, "Core " + id, "core-image-" + id,
                new RecommendationDescription(List.of(), List.of(), List.of("공격력")));
    }

    /** 선수마다 레벨·킬·어시스트가 다르다. 아군은 champion1~4, 상대는 champion5~9, 나는 champion10이다. */
    private ObjectNode body() throws Exception {
        return (ObjectNode) JSON.readTree("""
                {"myChampion":{"name":"champion10","position":"TOP","level":11,"kills":4,"deaths":2,"assists":3},
                 "purchasedItemIds":[],
                 "allies":[
                   {"name":"champion1","position":"JUNGLE","level":8,"kills":0,"assists":7,"itemIds":[3089,3340]},
                   {"name":"champion2","position":"MID","level":7,"kills":1,"assists":9,"itemIds":[]},
                   {"name":"champion3","position":"BOTTOM","level":9,"kills":2,"assists":5,"itemIds":[3031]},
                   {"name":"champion4","position":"SUPPORT","level":10,"kills":3,"assists":1,"itemIds":[]}],
                 "enemies":[
                   {"name":"champion5","position":"TOP","level":12,"kills":5,"assists":0,"itemIds":[3075]},
                   {"name":"champion6","position":"JUNGLE","level":13,"kills":6,"assists":2,"itemIds":[]},
                   {"name":"champion7","position":"MID","level":6,"kills":7,"assists":4,"itemIds":[3089,3020]},
                   {"name":"champion8","position":"BOTTOM","level":5,"kills":8,"assists":6,"itemIds":[]},
                   {"name":"champion9","position":"SUPPORT","level":4,"kills":9,"assists":8,"itemIds":[3109]}],
                 "tier":"EMERALD","patch":null,
                 "inventoryItems":[{"itemId":1036,"quantity":1}],"availableGold":1000,"gameTimeMs":600000}
                """);
    }

    private String json(ObjectNode body) throws Exception {
        return JSON.writeValueAsString(body);
    }

    @Test
    @DisplayName("v4와 같은 모양으로 응답한다 — Core와 조합 묶음, 상황 대응 아이템")
    void recommendV5_WhenRequestValid_RespondsInV4Shape() throws Exception {
        // when & then
        mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(json(body())))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.recommendedItems.length()").value(2))
                .andExpect(jsonPath("$.recommendedItems[0].id").value(3133))
                .andExpect(jsonPath("$.recommendedItems[0].name").value("Core 3133"))
                .andExpect(jsonPath("$.recommendedItems[0].description.traits[0]").value("공격력"))
                .andExpect(jsonPath("$.recommendedItems[0].purchaseBundle.items[0].itemId").value(3133))
                .andExpect(jsonPath("$.recommendedItems[0].purchaseBundle.additionalCost").value(750))
                .andExpect(jsonPath("$.recommendedItems[1].id").value(9999))
                .andExpect(jsonPath("$.recommendedItems[1]", hasKey("purchaseBundle")))
                .andExpect(jsonPath("$.situationalItems.length()").value(0));
    }

    @Test
    @DisplayName("요청의 10명 상태를 선수 순서 그대로 Core 추천에 넘기고, Core 요청은 v4와 같은 모양이다")
    void recommendV5_WhenRequestValid_PassesStateAndV4ShapedCoreRequest() throws Exception {
        // when
        mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(json(body())))
                .andExpect(status().isOk());

        // then
        ArgumentCaptor<NextItemRecommendationRequest> coreRequest =
                ArgumentCaptor.forClass(NextItemRecommendationRequest.class);
        ArgumentCaptor<ReportedGameState> state = ArgumentCaptor.forClass(ReportedGameState.class);
        verify(core).recommendNextItem(coreRequest.capture(), state.capture(), eq(Language.EN_US));
        verify(core, never()).recommendNextItem(any(), any());

        assertThat(coreRequest.getValue().myChampion()).isEqualTo(new ChampionDto("champion10", "TOP"));
        assertThat(coreRequest.getValue().allies()).extracting(ChampionDto::name)
                .containsExactly("champion1", "champion2", "champion3", "champion4");
        assertThat(coreRequest.getValue().patch()).isEqualTo("99.1");

        assertThat(state.getValue().me()).isEqualTo(new MyState(11, 4, 2, 3));
        assertThat(state.getValue().allies()).containsExactly(
                new ReportedGameState.Player(8, 0, 7, List.of(3089L, 3340L)),
                new ReportedGameState.Player(7, 1, 9, List.of()),
                new ReportedGameState.Player(9, 2, 5, List.of(3031L)),
                new ReportedGameState.Player(10, 3, 1, List.of()));
        assertThat(state.getValue().enemies()).containsExactly(
                new ReportedGameState.Player(12, 5, 0, List.of(3075L)),
                new ReportedGameState.Player(13, 6, 2, List.of()),
                new ReportedGameState.Player(6, 7, 4, List.of(3089L, 3020L)),
                new ReportedGameState.Player(5, 8, 6, List.of()),
                new ReportedGameState.Player(4, 9, 8, List.of(3109L)));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            myChampion | {"name":"champion10","position":"TOP","kills":4,"deaths":2,"assists":3} | level
            myChampion | {"name":"champion10","position":"TOP","level":0,"kills":4,"deaths":2,"assists":3} | level
            myChampion | {"name":"champion10","position":"TOP","level":11,"kills":-1,"deaths":2,"assists":3} | kills
            myChampion | {"name":"champion10","position":"TOP","level":11,"kills":4,"assists":3} | deaths
            myChampion | {"name":"champion10","position":"INVALID","level":11,"kills":4,"deaths":2,"assists":3} | position
            allies | [null,null,null,null] | allies
            enemies | [] | enemies
            purchasedItemIds | [1,2,3,4,5,6,7] | purchasedItemCountWithinLimit
            inventoryItems | [{"itemId":1036,"quantity":1},{"itemId":1036,"quantity":1}] | inventoryItemIdsUnique
            availableGold | -1 | availableGold
            """)
    @DisplayName("잘못되거나 빠진 값은 서비스 호출 전에 400으로 거부한다")
    void recommendV5_WhenFieldInvalid_RejectsWith400(String field, String value, String detail) throws Exception {
        // given
        ObjectNode body = body();
        body.set(field, JSON.readTree(value));

        // when & then
        mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail", containsString(detail)));
        verifyNoInteractions(core, runtime, champions, compositions);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            level | null | level
            kills | -1 | kills
            assists | null | assists
            itemIds | null | itemIds
            itemIds | [1,2,3,4,5,6,7,8] | itemIds
            itemIds | [0] | itemIds
            """)
    @DisplayName("상대 한 명의 상태 값이 잘못되거나 빠져도 400이다")
    void recommendV5_WhenEnemyStateInvalid_RejectsWith400(String field, String value, String detail) throws Exception {
        // given
        ObjectNode body = body();
        ((ObjectNode) body.get("enemies").get(2)).set(field, JSON.readTree(value));

        // when & then
        mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString(detail)));
        verifyNoInteractions(core, runtime, champions, compositions);
    }

    @Test
    @DisplayName("받지 않는 필드가 섞여 있어도 받는다 — 앱은 riotId를 함께 보낸다")
    void recommendV5_WhenUnknownFieldsSent_IgnoresThem() throws Exception {
        // given
        ObjectNode body = body();
        body.put("riotId", "player#KR1");
        ((ObjectNode) body.get("enemies").get(0)).put("deaths", 3);

        // when & then
        mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("조합 추천을 쓸 수 없으면 v4처럼 503이다")
    void recommendV5_WhenComponentUnavailable_Responds503LikeV4() throws Exception {
        // given
        when(runtime.isAvailable()).thenReturn(false);

        // when & then
        mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(json(body())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$", not(hasKey("stackTrace"))));
        // 어느 모델이 답하는지는 지표 꼬리표 때문에 먼저 묻는다. Core 추천은 하지 않는다
        verify(core, never()).recommendNextItem(any(), any(ReportedGameState.class), any());
    }

    @Test
    @DisplayName("Core 추천이 없으면 v4처럼 404이다")
    void recommendV5_WhenCoreNotFound_Responds404LikeV4() throws Exception {
        // given
        when(core.recommendNextItem(any(), any(ReportedGameState.class), any()))
                .thenThrow(new NextItemRecommendationNotFoundException("champion10", "TOP"));

        // when & then
        mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(json(body())))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("Accept-Language 헤더의 언어로 추천한다")
    void recommendV5_WhenAcceptLanguageGiven_UsesIt() throws Exception {
        // when
        mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(json(body()))
                        .header("Accept-Language", "ko-KR"))
                .andExpect(status().isOk());

        // then
        verify(core).recommendNextItem(any(), any(ReportedGameState.class), eq(Language.KO_KR));
    }

    @Test
    @DisplayName("API 문서에 새 API와 상태 필드가 나온다 — 프론트가 Swagger에서 요청 형식을 본다")
    void apiDocs_WhenRequested_DescribeV5RequestWithStateFields() throws Exception {
        // when & then
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/recommendations/v5'].post.requestBody.content['application/json']"
                        + ".schema['$ref']").value("#/components/schemas/NextItemRecommendationV5Request"))
                .andExpect(jsonPath("$.components.schemas.NextItemRecommendationV5Request.properties.myChampion['$ref']")
                        .value("#/components/schemas/MyChampionStateRequest"))
                .andExpect(jsonPath("$.components.schemas.NextItemRecommendationV5Request.properties.enemies.items['$ref']")
                        .value("#/components/schemas/PlayerStateRequest"))
                .andExpect(jsonPath("$.components.schemas.MyChampionStateRequest.required",
                        hasItems("name", "position", "level", "kills", "deaths", "assists")))
                .andExpect(jsonPath("$.components.schemas.PlayerStateRequest.required",
                        hasItems("name", "position", "level", "kills", "assists", "itemIds")))
                .andExpect(jsonPath("$.components.schemas.PlayerStateRequest.properties", not(hasKey("deaths"))))
                .andExpect(jsonPath("$.components.schemas.NextItemRecommendationV5Request.properties",
                        not(hasKey("purchasedItemCountWithinLimit"))));
    }
}
