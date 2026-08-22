package dfgg.presentation;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dfgg.application.ChampionBuildStatsRebuildResult;
import dfgg.application.ChampionBuildStatsRebuildService;
import dfgg.application.match.RiotMatchSyncService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RiotMatchControllerTest {

    private RiotMatchSyncService riotMatchSyncService;
    private ChampionBuildStatsRebuildService statsRebuildService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        riotMatchSyncService = mock(RiotMatchSyncService.class);
        statsRebuildService = mock(ChampionBuildStatsRebuildService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new RiotMatchController(riotMatchSyncService, statsRebuildService))
                .build();
    }

    @Test
    void 전달받은_플레이어와_기본_매치_범위를_동기화한다() throws Exception {
        mockMvc.perform(post("/admin/riot/matches")
                        .param("puuids", "puuid-1"))
                .andExpect(status().isNoContent());

        verify(riotMatchSyncService).syncMatches(List.of("puuid-1"), 0, 1);
    }

    @Test
    void 요청_파라미터로_플레이어와_매치_조회_범위를_지정한다() throws Exception {
        mockMvc.perform(post("/admin/riot/matches")
                        .param("puuids", "puuid-1", "puuid-2")
                        .param("start", "20")
                        .param("count", "50"))
                .andExpect(status().isNoContent());

        verify(riotMatchSyncService).syncMatches(
                List.of("puuid-1", "puuid-2"),
                20,
                50
        );
    }

    @Test
    void 수집할_플레이어를_전달하지_않으면_요청을_거부한다() throws Exception {
        mockMvc.perform(post("/admin/riot/matches"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(riotMatchSyncService);
    }

    @Test
    void 누락된_Timeline을_보완한다() throws Exception {
        mockMvc.perform(post("/admin/riot/matches/timelines"))
                .andExpect(status().isNoContent());

        verify(riotMatchSyncService).syncMissingTimelines();
    }

    @Test
    void 지정한_티어의_빌드_통계_집계_결과를_반환한다() throws Exception {
        when(statsRebuildService.rebuildAll("PLATINUM"))
                .thenReturn(new ChampionBuildStatsRebuildResult(
                        10,
                        8,
                        1,
                        1,
                        32,
                        List.of(new ChampionBuildStatsRebuildResult.Failure(
                                "KR_FAILED",
                                "IllegalStateException: invalid match data"
                        ))
                ));

        mockMvc.perform(post("/admin/riot/matches/stats")
                        .param("tier", "PLATINUM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMatches").value(10))
                .andExpect(jsonPath("$.processedMatches").value(8))
                .andExpect(jsonPath("$.skippedMissingTimeline").value(1))
                .andExpect(jsonPath("$.failedMatches").value(1))
                .andExpect(jsonPath("$.recordedSamples").value(32))
                .andExpect(jsonPath("$.failures[0].matchId").value("KR_FAILED"))
                .andExpect(jsonPath("$.failures[0].reason")
                        .value("IllegalStateException: invalid match data"));

        verify(statsRebuildService).rebuildAll("PLATINUM");
    }

    @Test
    void 잘못된_티어의_빌드_통계_재생성을_거부한다() throws Exception {
        mockMvc.perform(post("/admin/riot/matches/stats")
                        .param("tier", "MASTER"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 지정한_매치의_통계를_안전하게_재집계한다() throws Exception {
        when(statsRebuildService.replayOne("KR_1", "PLATINUM"))
                .thenReturn(new ChampionBuildStatsRebuildResult(1, 1, 0, 32));

        mockMvc.perform(post("/admin/riot/matches/KR_1/stats/replay")
                        .param("tier", "PLATINUM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMatches").value(1))
                .andExpect(jsonPath("$.processedMatches").value(1))
                .andExpect(jsonPath("$.recordedSamples").value(32));

        verify(statsRebuildService).replayOne("KR_1", "PLATINUM");
    }
}
