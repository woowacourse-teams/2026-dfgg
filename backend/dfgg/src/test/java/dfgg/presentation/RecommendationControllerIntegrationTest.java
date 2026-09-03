package dfgg.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dfgg.application.recommend.MultiBuildRecommendationService;
import dfgg.application.recommend.RecommendationService;
import dfgg.presentation.dto.ItemDto;
import dfgg.presentation.dto.response.BuildOptionResponse;
import dfgg.presentation.dto.response.MultiBuildRecommendationResponse;
import dfgg.presentation.dto.response.RecommendationResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecommendationService recommendationService;

    @MockitoBean
    private MultiBuildRecommendationService multiBuildRecommendationService;

    @Test
    void v1_추천_API는_기존_단일_빌드_응답을_반환한다() throws Exception {
        given(recommendationService.recommend(any())).willReturn(
                new RecommendationResponse(
                        "말파이트",
                        "TOP",
                        List.of(new ItemDto(1L, "아이템"))
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
                                        List.of(new ItemDto(1L, "아이템"))
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
}
