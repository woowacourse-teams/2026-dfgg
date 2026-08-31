package dfgg.presentation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dfgg.application.RiotCollectionOrchestrator;
import dfgg.application.match.RiotMatchSyncService;
import dfgg.application.stats.ChampionBuildStatsRebuildMatchService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RiotMatchControllerTest {

    private RiotMatchSyncService riotMatchSyncService;
    private RiotCollectionOrchestrator collectionOrchestrator;
    private ChampionBuildStatsRebuildMatchService statsRebuildService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        riotMatchSyncService = mock(RiotMatchSyncService.class);
        collectionOrchestrator = mock(RiotCollectionOrchestrator.class);
        statsRebuildService = mock(ChampionBuildStatsRebuildMatchService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new RiotMatchController(
                        riotMatchSyncService,
                        collectionOrchestrator,
                        statsRebuildService
                ))
                .build();
    }

    @Test
    void 전달받은_플레이어와_기본_매치_범위를_동기화한다() throws Exception {
        mockMvc.perform(post("/admin/riot/matches")
                        .param("puuids", "puuid-1"))
                .andExpect(status().isNoContent());

        verify(riotMatchSyncService).syncMatches(List.of("puuid-1"), 0, 1);
        verifyNoInteractions(collectionOrchestrator);
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
        verifyNoInteractions(collectionOrchestrator);
    }

    @Test
    void 누락된_Timeline을_보완한다() throws Exception {
        mockMvc.perform(post("/admin/riot/matches/timelines"))
                .andExpect(status().isNoContent());

        verify(riotMatchSyncService).syncMissingTimelines();
        verifyNoInteractions(collectionOrchestrator);
    }

    @Test
    void 지정한_티어로_신규_매치를_즉시_집계하고_미완료_통계를_백필한다() throws Exception {
        mockMvc.perform(post("/admin/riot/matches/stats")
                        .param("tier", "PLATINUM"))
                .andExpect(status().isNoContent());

        InOrder order = inOrder(collectionOrchestrator, statsRebuildService);
        order.verify(collectionOrchestrator).normalizeAndAggregatePendingMatches("PLATINUM");
        order.verify(statsRebuildService).rebuildAll("PLATINUM");
    }

    @Test
    void 신규_매치_처리가_실패해도_미완료_통계_백필을_실행한다() {
        doThrow(new IllegalStateException("statistics unavailable"))
                .when(collectionOrchestrator)
                .normalizeAndAggregatePendingMatches("PLATINUM");
        RiotMatchController controller = new RiotMatchController(
                riotMatchSyncService,
                collectionOrchestrator,
                statsRebuildService
        );

        assertThatThrownBy(() -> controller.rebuildStats("PLATINUM"))
                .isInstanceOf(IllegalStateException.class);

        verify(statsRebuildService).rebuildAll("PLATINUM");
    }

    @Test
    void Master_티어의_빌드_통계를_재생성한다() throws Exception {
        mockMvc.perform(post("/admin/riot/matches/stats")
                        .param("tier", "MASTER"))
                .andExpect(status().isNoContent());

        InOrder order = inOrder(collectionOrchestrator, statsRebuildService);
        order.verify(collectionOrchestrator).normalizeAndAggregatePendingMatches("MASTER");
        order.verify(statsRebuildService).rebuildAll("MASTER");
    }

    @Test
    void Grandmaster_티어의_빌드_통계를_재생성한다() throws Exception {
        mockMvc.perform(post("/admin/riot/matches/stats")
                        .param("tier", "GRANDMASTER"))
                .andExpect(status().isNoContent());

        InOrder order = inOrder(collectionOrchestrator, statsRebuildService);
        order.verify(collectionOrchestrator).normalizeAndAggregatePendingMatches("GRANDMASTER");
        order.verify(statsRebuildService).rebuildAll("GRANDMASTER");
    }

    @Test
    void Challenger_티어의_빌드_통계를_재생성한다() throws Exception {
        mockMvc.perform(post("/admin/riot/matches/stats")
                        .param("tier", "CHALLENGER"))
                .andExpect(status().isNoContent());

        InOrder order = inOrder(collectionOrchestrator, statsRebuildService);
        order.verify(collectionOrchestrator).normalizeAndAggregatePendingMatches("CHALLENGER");
        order.verify(statsRebuildService).rebuildAll("CHALLENGER");
    }

    @Test
    void 지원하지_않는_티어의_빌드_통계_재생성을_거부한다() throws Exception {
        mockMvc.perform(post("/admin/riot/matches/stats")
                        .param("tier", "MYTHIC"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(collectionOrchestrator, statsRebuildService);
    }

    @Test
    void 지정한_매치의_통계를_안전하게_재집계한다() throws Exception {
        mockMvc.perform(post("/admin/riot/matches/KR_1/stats/replay")
                        .param("tier", "PLATINUM"))
                .andExpect(status().isNoContent());

        verify(statsRebuildService).replayOne("KR_1", "PLATINUM");
        verifyNoInteractions(collectionOrchestrator);
    }

    @Test
    void Master_매치의_통계를_재집계한다() throws Exception {
        mockMvc.perform(post("/admin/riot/matches/KR_MASTER/stats/replay")
                        .param("tier", "MASTER"))
                .andExpect(status().isNoContent());

        verify(statsRebuildService).replayOne("KR_MASTER", "MASTER");
        verifyNoInteractions(collectionOrchestrator);
    }
}
