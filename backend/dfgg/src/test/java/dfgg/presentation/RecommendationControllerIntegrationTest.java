package dfgg.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dfgg.application.recommend.MultiBuildRecommendationService;
import dfgg.application.recommend.NextItemRecommendationService;
import dfgg.application.recommend.RecommendationService;
import dfgg.domain.language.Language;
import dfgg.presentation.dto.ItemDto;
import dfgg.presentation.dto.response.BuildOptionResponse;
import dfgg.presentation.dto.response.MultiBuildRecommendationResponse;
import dfgg.presentation.dto.response.NextItemRecommendationResponse;
import dfgg.presentation.dto.response.RecommendationResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "local"})
class RecommendationControllerIntegrationTest {

    private static final String VALID_REQUEST = """
            {
              "myChampion": {"name": "말파이트", "position": "TOP"},
              "allies": [
                {"name": "아군1", "position": "JUNGLE"},
                {"name": "아군2", "position": "MID"},
                {"name": "아군3", "position": "BOTTOM"},
                {"name": "아군4", "position": "SUPPORT"}
              ],
              "enemies": [
                {"name": "적군1", "position": "TOP"},
                {"name": "적군2", "position": "JUNGLE"},
                {"name": "적군3", "position": "MID"},
                {"name": "적군4", "position": "BOTTOM"},
                {"name": "적군5", "position": "SUPPORT"}
              ]
            }
            """;

    private static final String V3_REQUEST = """
            {
              "myChampion": {"name": "말파이트", "position": "TOP"},
              "purchasedItemIds": [],
              "allies": [
                {"name": "아군1", "position": "JUNGLE"},
                {"name": "아군2", "position": "MID"},
                {"name": "아군3", "position": "BOTTOM"},
                {"name": "아군4", "position": "SUPPORT"}
              ],
              "enemies": [
                {"name": "적군1", "position": "TOP"},
                {"name": "적군2", "position": "JUNGLE"},
                {"name": "적군3", "position": "MID"},
                {"name": "적군4", "position": "BOTTOM"},
                {"name": "적군5", "position": "SUPPORT"}
              ]
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecommendationService recommendationService;

    @MockitoBean
    private MultiBuildRecommendationService multiBuildRecommendationService;

    @MockitoBean
    private NextItemRecommendationService nextItemRecommendationService;

    @Test
    void v1_추천_API는_기존_단일_빌드_응답을_반환한다() throws Exception {
        given(recommendationService.recommend(any())).willReturn(
                new RecommendationResponse(
                        "말파이트",
                        "TOP",
                        List.of(new ItemDto(1L, "아이템", "https://test-bucket.s3.ap-northeast-2.amazonaws.com/dfgg/images/99.1/items/1.png"))
                )
        );

        mockMvc.perform(post("/api/recommendations/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.champion").value("말파이트"))
                .andExpect(jsonPath("$.items[0].id").value(1));
    }

    @Test
    void v2_추천_API는_사용_가능한_빌드와_사용_불가능한_방향을_함께_반환한다() throws Exception {
        given(multiBuildRecommendationService.recommend(any())).willReturn(
                new MultiBuildRecommendationResponse(
                        "말파이트",
                        "TOP",
                        List.of(
                                new BuildOptionResponse(
                                        "TANK",
                                        "PHYSICAL_DAMAGE",
                                        List.of(new ItemDto(1L, "아이템", "https://test-bucket.s3.ap-northeast-2.amazonaws.com/dfgg/images/99.1/items/1.png"))
                                ),
                                new BuildOptionResponse(
                                        "TANK",
                                        "MAGIC_DAMAGE",
                                        null
                                )
                        )
                )
        );

        mockMvc.perform(post("/api/recommendations/v2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.champion").value("말파이트"))
                .andExpect(jsonPath("$.position").value("TOP"))
                .andExpect(jsonPath("$.builds.length()").value(2))
                .andExpect(jsonPath("$.builds[0].build[0].id").value(1))
                .andExpect(jsonPath("$.builds[0].available").doesNotExist())
                .andExpect(jsonPath("$.builds[0].recommended").doesNotExist())
                .andExpect(jsonPath("$.builds[1].build").isEmpty());
    }

    @Test
    @DisplayName("v3는 Accept-Language 헤더의 언어로 이름을 낸다")
    void v3_UsesLanguageFromAcceptLanguageHeader() throws Exception {
        given(nextItemRecommendationService.recommendNextItem(any(), any()))
                .willReturn(new NextItemRecommendationResponse(List.of()));

        mockMvc.perform(post("/api/recommendations/v3")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "ko-KR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(V3_REQUEST))
                .andExpect(status().isOk());

        verify(nextItemRecommendationService).recommendNextItem(any(), eq(Language.KO_KR));
    }

    @Test
    @DisplayName("v3는 Accept-Language 헤더가 없으면 영어로 낸다")
    void v3_WhenAcceptLanguageIsAbsent_UsesEnglish() throws Exception {
        given(nextItemRecommendationService.recommendNextItem(any(), any()))
                .willReturn(new NextItemRecommendationResponse(List.of()));

        mockMvc.perform(post("/api/recommendations/v3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(V3_REQUEST))
                .andExpect(status().isOk());

        verify(nextItemRecommendationService).recommendNextItem(any(), eq(Language.EN_US));
    }
}
