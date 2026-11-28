package dfgg.presentation;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dfgg.application.champion.ChampionService;
import dfgg.common.exception.ChampionNotFoundException;
import dfgg.domain.champion.Champion;
import dfgg.domain.rune.NormalizedMatchParticipantRune;
import dfgg.domain.rune.NormalizedMatchParticipantRuneRepository;
import dfgg.domain.rune.Rune;
import dfgg.domain.rune.RuneKind;
import dfgg.domain.rune.RunePage;
import dfgg.domain.rune.RuneRepository;
import dfgg.domain.rune.StatShards;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** HTTP 바인딩부터 룬 기록 조회, 페이지 선택, 카탈로그 이름·이미지 붙이기까지 연결한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "recommendation.rune.patch=16.19",
        "recommendation.rune.minimum-games=3",
        "recommendation.rune.minimum-path-games=1",
        "recommendation.rune.page-count=3"
})
class RuneRecommendationControllerTest {

    private static final String ENDPOINT = "/api/recommendations/runes";
    private static final String IMAGE_ROOT =
            "https://test-bucket.s3.ap-northeast-2.amazonaws.com/dfgg/images/16.19/runes/";
    private static final int JAYCE_ID = 126;
    private static final RunePage CONQUEROR_PAGE = new RunePage(8000, List.of(8010, 9111, 9104, 8299),
            8400, List.of(8444, 8242), new StatShards(5005, 5008, 5001));
    private static final RunePage PHASE_RUSH_PAGE = new RunePage(8200, List.of(8230, 8226, 8233, 8236),
            8300, List.of(8304, 8345), new StatShards(5005, 5008, 5001));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NormalizedMatchParticipantRuneRepository participantRuneRepository;

    @Autowired
    private RuneRepository runeRepository;

    @MockitoBean
    private ChampionService championService;

    @BeforeEach
    void setUp() {
        cleanUp();
        Champion jayce = mock(Champion.class);
        when(jayce.getChampionId()).thenReturn((long) JAYCE_ID);
        when(championService.findChampionByName("Jayce")).thenReturn(jayce);
        when(championService.findChampionByName("Nobody")).thenThrow(new ChampionNotFoundException("Nobody"));
        runeRepository.saveAll(List.of(
                new Rune(8000, RuneKind.STYLE, Map.of("ko-KR", "정밀", "en-US", "Precision")),
                new Rune(8010, RuneKind.PERK, Map.of("ko-KR", "정복자", "en-US", "Conqueror"))
        ));
    }

    @AfterEach
    void cleanUp() {
        participantRuneRepository.deleteAllInBatch();
        runeRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("묶음 판 수 순으로 페이지를 내고, 룬에 요청 언어의 이름과 추천 패치 이미지를 붙인다")
    void recommendRunes_ReturnsRankedPagesWithCatalogNames() throws Exception {
        // given
        participantRuneRepository.saveAll(List.of(
                jayceMid("KR_1", CONQUEROR_PAGE),
                jayceMid("KR_2", CONQUEROR_PAGE),
                jayceMid("KR_3", PHASE_RUSH_PAGE)
        ));

        // when
        ResultActions result = recommend("MID", "ko");

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.patch").value("16.19"))
                .andExpect(jsonPath("$.championGames").value(3))
                .andExpect(jsonPath("$.pages.length()").value(2))
                .andExpect(jsonPath("$.pages[0].rank").value(1))
                .andExpect(jsonPath("$.pages[0].primaryStyle.name").value("정밀"))
                .andExpect(jsonPath("$.pages[0].primaryStyle.imageUrl").value(IMAGE_ROOT + "8000.png"))
                .andExpect(jsonPath("$.pages[0].keystone.id").value(8010))
                .andExpect(jsonPath("$.pages[0].keystone.name").value("정복자"))
                .andExpect(jsonPath("$.pages[0].primaryRunes[*].id", contains(9111, 9104, 8299)))
                .andExpect(jsonPath("$.pages[0].subStyle.id").value(8400))
                .andExpect(jsonPath("$.pages[0].subRunes[*].id", contains(8444, 8242)))
                .andExpect(jsonPath("$.pages[0].statShards[*].id", contains(5005, 5008, 5001)))
                .andExpect(jsonPath("$.pages[0].evidence.games").value(2))
                .andExpect(jsonPath("$.pages[0].evidence.pickRate").value(2.0 / 3))
                .andExpect(jsonPath("$.pages[1].keystone.id").value(8230));
    }

    @Test
    @DisplayName("카탈로그에 없는 룬은 id만 내고 이름과 이미지를 비운다")
    void recommendRunes_WhenRuneMissingInCatalog_ReturnsIdOnly() throws Exception {
        // given
        participantRuneRepository.saveAll(List.of(
                jayceMid("KR_1", PHASE_RUSH_PAGE),
                jayceMid("KR_2", PHASE_RUSH_PAGE),
                jayceMid("KR_3", PHASE_RUSH_PAGE)
        ));

        // when
        ResultActions result = recommend("MID", "en");

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.pages[0].keystone.id").value(8230))
                .andExpect(jsonPath("$.pages[0].keystone.name", nullValue()))
                .andExpect(jsonPath("$.pages[0].keystone.imageUrl", nullValue()));
    }

    @Test
    @DisplayName("챔피언·포지션 판 수가 기준보다 적으면 판 수만 내고 페이지를 비운다")
    void recommendRunes_WhenGamesBelowMinimum_ReturnsEmptyPages() throws Exception {
        // given
        participantRuneRepository.saveAll(List.of(
                jayceMid("KR_1", CONQUEROR_PAGE),
                jayceMid("KR_2", CONQUEROR_PAGE)
        ));

        // when
        ResultActions result = recommend("MID", "ko");

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.patch").value("16.19"))
                .andExpect(jsonPath("$.championGames").value(2))
                .andExpect(jsonPath("$.pages.length()").value(0));
    }

    @Test
    @DisplayName("지원하지 않는 포지션은 400으로 거절한다")
    void recommendRunes_WhenPositionInvalid_ReturnsBadRequest() throws Exception {
        recommend("MIDDLE", "ko").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("존재하지 않는 챔피언은 400으로 거절한다")
    void recommendRunes_WhenChampionNotFound_ReturnsBadRequest() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"myChampion": {"name": "Nobody", "position": "TOP"}}
                                """))
                .andExpect(status().isBadRequest());
    }

    private ResultActions recommend(String position, String language) throws Exception {
        return mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_LANGUAGE, language)
                .content("""
                        {"myChampion": {"name": "Jayce", "position": "%s"}}
                        """.formatted(position)));
    }

    private NormalizedMatchParticipantRune jayceMid(String matchId, RunePage page) {
        return new NormalizedMatchParticipantRune(matchId, 1, "16.19", JAYCE_ID, 100, "MIDDLE", true, page);
    }
}
