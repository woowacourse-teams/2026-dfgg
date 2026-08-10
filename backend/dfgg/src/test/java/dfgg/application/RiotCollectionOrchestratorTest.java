package dfgg.application;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dfgg.infrastructure.config.RiotSchedulerProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class RiotCollectionOrchestratorTest {

    private ChampionSyncService championSyncService;
    private ItemSyncService itemSyncService;
    private RiotPlayerSyncService playerSyncService;
    private RiotMatchSyncService matchSyncService;
    private ChampionBuildStatsRebuildService statsRebuildService;
    private RiotSchedulerProperties properties;
    private RiotCollectionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        championSyncService = mock(ChampionSyncService.class);
        itemSyncService = mock(ItemSyncService.class);
        playerSyncService = mock(RiotPlayerSyncService.class);
        matchSyncService = mock(RiotMatchSyncService.class);
        statsRebuildService = mock(ChampionBuildStatsRebuildService.class);
        properties = new RiotSchedulerProperties();
        properties.setPlayerPageSize(2);

        orchestrator = new RiotCollectionOrchestrator(
                properties,
                championSyncService,
                itemSyncService,
                playerSyncService,
                matchSyncService,
                statsRebuildService
        );
    }

    @Test
    void 메타데이터부터_통계까지_순서대로_실행하고_결과를_합산한다() {
        when(playerSyncService.syncLeagueEntries("RANKED_SOLO_5x5", "PLATINUM", "I", 1))
                .thenReturn(2);
        when(matchSyncService.syncMatches(0, 2, 0, 20))
                .thenReturn(new RiotMatchSyncService.SyncResult(1, 2, 1, 3, List.of()));
        when(matchSyncService.syncMissingTimelinesWithResult())
                .thenReturn(new RiotMatchSyncService.SyncResult(0, 0, 1, 0, List.of()));
        when(statsRebuildService.rebuildAll("PLATINUM"))
                .thenReturn(new ChampionBuildStatsRebuildResult(2, 2, 0, 64));

        orchestrator.runOnce();

        InOrder order = inOrder(
                championSyncService,
                itemSyncService,
                playerSyncService,
                matchSyncService,
                statsRebuildService
        );
        order.verify(championSyncService).syncChampions();
        order.verify(itemSyncService).syncCoreItem();
        order.verify(playerSyncService)
                .syncLeagueEntries("RANKED_SOLO_5x5", "PLATINUM", "I", 1);
        order.verify(matchSyncService).syncMatches(0, 2, 0, 20);
        order.verify(matchSyncService).syncMissingTimelinesWithResult();
        order.verify(statsRebuildService).rebuildAll("PLATINUM");
    }

    @Test
    void 한_단계가_실패해도_후속_단계를_계속한다() {
        when(playerSyncService.syncLeagueEntries("RANKED_SOLO_5x5", "PLATINUM", "I", 1))
                .thenThrow(new IllegalStateException("league unavailable"));
        when(matchSyncService.syncMatches(0, 2, 0, 20))
                .thenReturn(new RiotMatchSyncService.SyncResult(0, 0, 0, 0, List.of()));
        when(matchSyncService.syncMissingTimelinesWithResult())
                .thenReturn(new RiotMatchSyncService.SyncResult(0, 0, 0, 0, List.of()));
        when(statsRebuildService.rebuildAll("PLATINUM"))
                .thenReturn(new ChampionBuildStatsRebuildResult(0, 0, 0, 0));

        orchestrator.runOnce();
        verify(matchSyncService).syncMatches(0, 2, 0, 20);
        verify(matchSyncService).syncMissingTimelinesWithResult();
        verify(statsRebuildService).rebuildAll("PLATINUM");
    }

    @Test
    void 스케줄이_성공할_때마다_리그_페이지와_매치_시작_위치를_증가시킨다() {
        when(matchSyncService.syncMatches(0, 2, 0, 20))
                .thenReturn(new RiotMatchSyncService.SyncResult(1, 0, 0, 0, List.of()));
        when(matchSyncService.syncMatches(0, 2, 20, 20))
                .thenReturn(new RiotMatchSyncService.SyncResult(1, 0, 0, 0, List.of()));
        when(matchSyncService.syncMissingTimelinesWithResult())
                .thenReturn(new RiotMatchSyncService.SyncResult(0, 0, 0, 0, List.of()));
        when(statsRebuildService.rebuildAll("PLATINUM"))
                .thenReturn(new ChampionBuildStatsRebuildResult(0, 0, 0, 0));

        orchestrator.runOnce();
        orchestrator.runOnce();

        verify(playerSyncService)
                .syncLeagueEntries("RANKED_SOLO_5x5", "PLATINUM", "I", 1);
        verify(playerSyncService)
                .syncLeagueEntries("RANKED_SOLO_5x5", "PLATINUM", "I", 2);
        verify(matchSyncService).syncMatches(0, 2, 0, 20);
        verify(matchSyncService).syncMatches(0, 2, 20, 20);
    }

    @Test
    void 수집이_실패하면_해당_범위를_다음_스케줄에서_다시_시도한다() {
        when(playerSyncService.syncLeagueEntries("RANKED_SOLO_5x5", "PLATINUM", "I", 1))
                .thenThrow(new IllegalStateException("league unavailable"));
        RiotMatchSyncService.Failure failure = new RiotMatchSyncService.Failure(
                "MATCH", "KR_1", "riot unavailable"
        );
        when(matchSyncService.syncMatches(0, 2, 0, 20))
                .thenReturn(new RiotMatchSyncService.SyncResult(1, 0, 0, 0, List.of(failure)));
        when(matchSyncService.syncMissingTimelinesWithResult())
                .thenReturn(new RiotMatchSyncService.SyncResult(0, 0, 0, 0, List.of()));
        when(statsRebuildService.rebuildAll("PLATINUM"))
                .thenReturn(new ChampionBuildStatsRebuildResult(0, 0, 0, 0));

        orchestrator.runOnce();
        orchestrator.runOnce();

        verify(playerSyncService, times(2))
                .syncLeagueEntries("RANKED_SOLO_5x5", "PLATINUM", "I", 1);
        verify(matchSyncService, times(2)).syncMatches(0, 2, 0, 20);
    }

    @Test
    void 시작_디비전부터_I까지_진행한_뒤_다음_리그_페이지로_이동한다() {
        properties.setDivisions(List.of("IV"));
        when(matchSyncService.syncMatches(0, 2, 0, 20))
                .thenReturn(new RiotMatchSyncService.SyncResult(0, 0, 0, 0, List.of()));
        when(matchSyncService.syncMatches(0, 2, 20, 20))
                .thenReturn(new RiotMatchSyncService.SyncResult(0, 0, 0, 0, List.of()));
        when(matchSyncService.syncMatches(0, 2, 40, 20))
                .thenReturn(new RiotMatchSyncService.SyncResult(0, 0, 0, 0, List.of()));
        when(matchSyncService.syncMatches(0, 2, 60, 20))
                .thenReturn(new RiotMatchSyncService.SyncResult(0, 0, 0, 0, List.of()));
        when(matchSyncService.syncMatches(0, 2, 80, 20))
                .thenReturn(new RiotMatchSyncService.SyncResult(0, 0, 0, 0, List.of()));
        when(matchSyncService.syncMissingTimelinesWithResult())
                .thenReturn(new RiotMatchSyncService.SyncResult(0, 0, 0, 0, List.of()));
        when(statsRebuildService.rebuildAll("PLATINUM"))
                .thenReturn(new ChampionBuildStatsRebuildResult(0, 0, 0, 0));

        orchestrator.runOnce();
        orchestrator.runOnce();
        orchestrator.runOnce();
        orchestrator.runOnce();
        orchestrator.runOnce();

        verify(playerSyncService)
                .syncLeagueEntries("RANKED_SOLO_5x5", "PLATINUM", "IV", 1);
        verify(playerSyncService)
                .syncLeagueEntries("RANKED_SOLO_5x5", "PLATINUM", "III", 1);
        verify(playerSyncService)
                .syncLeagueEntries("RANKED_SOLO_5x5", "PLATINUM", "II", 1);
        verify(playerSyncService)
                .syncLeagueEntries("RANKED_SOLO_5x5", "PLATINUM", "I", 1);
        verify(playerSyncService)
                .syncLeagueEntries("RANKED_SOLO_5x5", "PLATINUM", "IV", 2);
    }

}
