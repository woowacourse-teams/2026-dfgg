package dfgg.application;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dfgg.application.match.MatchNormalizationService;
import dfgg.application.match.RiotMatchSyncService;
import dfgg.application.player.RiotPlayerSyncService;
import dfgg.application.stats.ChampionBuildStatsMatchService;
import dfgg.domain.match.NormalizedMatch;
import dfgg.infrastructure.config.RiotSchedulerProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class RiotCollectionOrchestratorTest {

    private RiotPlayerSyncService playerSyncService;
    private RiotMatchSyncService matchSyncService;
    private MatchNormalizationService matchNormalizationService;
    private ChampionBuildStatsMatchService statsMatchService;
    private RiotSchedulerProperties properties;
    private RiotCollectionOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        playerSyncService = mock(RiotPlayerSyncService.class);
        matchSyncService = mock(RiotMatchSyncService.class);
        matchNormalizationService = mock(MatchNormalizationService.class);
        statsMatchService = mock(ChampionBuildStatsMatchService.class);
        properties = new RiotSchedulerProperties();
        properties.setPlayerPageSize(2);
        when(playerSyncService.syncLeagueEntries(
                anyString(), anyString(), anyString(), anyInt()
        )).thenReturn(new RiotPlayerSyncService.SyncResult(0, List.of()));
        when(matchSyncService.syncMissingTimelines())
                .thenReturn(new RiotMatchSyncService.SyncResult(0, 0, 0, 0, List.of()));

        orchestrator = new RiotCollectionOrchestrator(
                properties,
                playerSyncService,
                matchSyncService,
                matchNormalizationService,
                statsMatchService
        );
    }

    @Test
    void 플레이어부터_통계까지_순서대로_실행하고_결과를_합산한다() {
        when(playerSyncService.syncLeagueEntries("RANKED_SOLO_5x5", "PLATINUM", "I", 1))
                .thenReturn(new RiotPlayerSyncService.SyncResult(
                        2,
                        List.of("puuid-1", "puuid-2")
                ));
        when(matchSyncService.syncMatches(List.of("puuid-1", "puuid-2"), 0, 20))
                .thenReturn(new RiotMatchSyncService.SyncResult(1, 2, 1, 3, List.of()));
        NormalizedMatch normalized = normalizedMatch("KR_1");
        when(matchNormalizationService.findPendingMatchIdsAfter(""))
                .thenReturn(List.of("KR_1"));
        when(matchNormalizationService.findPendingMatchIdsAfter("KR_1"))
                .thenReturn(List.of());
        when(matchNormalizationService.normalize("KR_1")).thenReturn(normalized);
        orchestrator.runOnce();

        InOrder order = inOrder(
                playerSyncService,
                matchSyncService,
                matchNormalizationService,
                statsMatchService
        );
        order.verify(playerSyncService)
                .syncLeagueEntries("RANKED_SOLO_5x5", "PLATINUM", "I", 1);
        order.verify(matchSyncService).syncMatches(List.of("puuid-1", "puuid-2"), 0, 20);
        order.verify(matchSyncService).syncMissingTimelines();
        order.verify(matchNormalizationService).findPendingMatchIdsAfter("");
        order.verify(matchNormalizationService).normalize("KR_1");
        order.verify(matchNormalizationService).save(normalized);
        order.verify(statsMatchService).registerMatchStats(normalized, "PLATINUM");
        order.verify(matchNormalizationService).findPendingMatchIdsAfter("KR_1");
    }

    @Test
    void 한_매치의_정규화가_실패해도_다음_매치와_통계_집계를_계속한다() {
        NormalizedMatch succeeded = normalizedMatch("KR_SUCCEEDED");
        when(matchNormalizationService.findPendingMatchIdsAfter(""))
                .thenReturn(List.of("KR_FAILED", "KR_SUCCEEDED"));
        when(matchNormalizationService.findPendingMatchIdsAfter("KR_SUCCEEDED"))
                .thenReturn(List.of());
        when(matchNormalizationService.normalize("KR_FAILED"))
                .thenThrow(new IllegalStateException("invalid match data"));
        when(matchNormalizationService.normalize("KR_SUCCEEDED")).thenReturn(succeeded);
        orchestrator.runOnce();

        InOrder order = inOrder(matchSyncService, matchNormalizationService, statsMatchService);
        order.verify(matchSyncService).syncMissingTimelines();
        order.verify(matchNormalizationService).findPendingMatchIdsAfter("");
        order.verify(matchNormalizationService).normalize("KR_FAILED");
        order.verify(matchNormalizationService).normalize("KR_SUCCEEDED");
        order.verify(matchNormalizationService).save(succeeded);
        order.verify(statsMatchService).registerMatchStats(succeeded, "PLATINUM");
        order.verify(matchNormalizationService).findPendingMatchIdsAfter("KR_SUCCEEDED");
    }

    @Test
    void 한_매치의_정규화_저장이_실패해도_다음_매치와_통계_집계를_계속한다() {
        NormalizedMatch failed = normalizedMatch("KR_FAILED");
        NormalizedMatch succeeded = normalizedMatch("KR_SUCCEEDED");
        when(matchNormalizationService.findPendingMatchIdsAfter(""))
                .thenReturn(List.of("KR_FAILED", "KR_SUCCEEDED"));
        when(matchNormalizationService.findPendingMatchIdsAfter("KR_SUCCEEDED"))
                .thenReturn(List.of());
        when(matchNormalizationService.normalize("KR_FAILED")).thenReturn(failed);
        when(matchNormalizationService.normalize("KR_SUCCEEDED")).thenReturn(succeeded);
        doThrow(new IllegalStateException("database unavailable"))
                .when(matchNormalizationService).save(failed);
        orchestrator.runOnce();

        InOrder order = inOrder(matchNormalizationService, statsMatchService);
        order.verify(matchNormalizationService).normalize("KR_FAILED");
        order.verify(matchNormalizationService).save(failed);
        order.verify(matchNormalizationService).normalize("KR_SUCCEEDED");
        order.verify(matchNormalizationService).save(succeeded);
        order.verify(statsMatchService).registerMatchStats(succeeded, "PLATINUM");
        verify(statsMatchService, never()).registerMatchStats(failed, "PLATINUM");
    }

    @Test
    void 정규화_대상_조회가_실패하면_통계를_집계하지_않는다() {
        when(matchNormalizationService.findPendingMatchIdsAfter(""))
                .thenThrow(new IllegalStateException("database unavailable"));
        orchestrator.runOnce();

        verify(matchNormalizationService).findPendingMatchIdsAfter("");
        verifyNoInteractions(statsMatchService);
    }

    @Test
    void 한_매치의_통계_집계가_실패해도_다음_매치의_정규화와_통계_집계를_계속한다() {
        NormalizedMatch failed = normalizedMatch("KR_FAILED");
        NormalizedMatch succeeded = normalizedMatch("KR_SUCCEEDED");
        when(matchNormalizationService.findPendingMatchIdsAfter(""))
                .thenReturn(List.of("KR_FAILED", "KR_SUCCEEDED"));
        when(matchNormalizationService.findPendingMatchIdsAfter("KR_SUCCEEDED"))
                .thenReturn(List.of());
        when(matchNormalizationService.normalize("KR_FAILED")).thenReturn(failed);
        when(matchNormalizationService.normalize("KR_SUCCEEDED")).thenReturn(succeeded);
        doThrow(new IllegalStateException("statistics unavailable"))
                .when(statsMatchService).registerMatchStats(failed, "PLATINUM");

        orchestrator.runOnce();

        InOrder order = inOrder(matchNormalizationService, statsMatchService);
        order.verify(matchNormalizationService).normalize("KR_FAILED");
        order.verify(matchNormalizationService).save(failed);
        order.verify(statsMatchService).registerMatchStats(failed, "PLATINUM");
        order.verify(matchNormalizationService).normalize("KR_SUCCEEDED");
        order.verify(matchNormalizationService).save(succeeded);
        order.verify(statsMatchService).registerMatchStats(succeeded, "PLATINUM");
    }

    @Test
    void 한_단계가_실패해도_후속_단계를_계속한다() {
        when(playerSyncService.syncLeagueEntries("RANKED_SOLO_5x5", "PLATINUM", "I", 1))
                .thenThrow(new IllegalStateException("league unavailable"));
        orchestrator.runOnce();
        verify(matchSyncService, never()).syncMatches(anyList(), eq(0), eq(20));
        verify(matchSyncService).syncMissingTimelines();
        verifyNoInteractions(statsMatchService);
    }

    @Test
    void 스케줄이_성공할_때마다_다음_리그_페이지의_PUUID만_매치_수집에_전달한다() {
        when(playerSyncService.syncLeagueEntries("RANKED_SOLO_5x5", "PLATINUM", "I", 1))
                .thenReturn(new RiotPlayerSyncService.SyncResult(1, List.of("puuid-1")));
        when(playerSyncService.syncLeagueEntries("RANKED_SOLO_5x5", "PLATINUM", "I", 2))
                .thenReturn(new RiotPlayerSyncService.SyncResult(1, List.of("puuid-2")));
        when(matchSyncService.syncMatches(anyList(), eq(0), eq(20)))
                .thenReturn(new RiotMatchSyncService.SyncResult(1, 0, 0, 0, List.of()));
        orchestrator.runOnce();
        orchestrator.runOnce();

        verify(playerSyncService)
                .syncLeagueEntries("RANKED_SOLO_5x5", "PLATINUM", "I", 1);
        verify(playerSyncService)
                .syncLeagueEntries("RANKED_SOLO_5x5", "PLATINUM", "I", 2);
        verify(matchSyncService).syncMatches(List.of("puuid-1"), 0, 20);
        verify(matchSyncService).syncMatches(List.of("puuid-2"), 0, 20);
    }

    @Test
    void 수집이_실패하면_해당_범위를_다음_스케줄에서_다시_시도한다() {
        when(playerSyncService.syncLeagueEntries("RANKED_SOLO_5x5", "PLATINUM", "I", 1))
                .thenThrow(new IllegalStateException("league unavailable"));
        orchestrator.runOnce();
        orchestrator.runOnce();

        verify(playerSyncService, times(2))
                .syncLeagueEntries("RANKED_SOLO_5x5", "PLATINUM", "I", 1);
        verify(matchSyncService, never()).syncMatches(anyList(), eq(0), eq(20));
    }

    @Test
    void 시작_디비전부터_I까지_진행한_뒤_다음_리그_페이지로_이동한다() {
        properties.setDivisions(List.of("IV"));
        when(playerSyncService.syncLeagueEntries(
                anyString(), anyString(), anyString(), anyInt()
        )).thenReturn(new RiotPlayerSyncService.SyncResult(0, List.of("puuid-1")));
        when(matchSyncService.syncMatches(anyList(), eq(0), eq(20)))
                .thenReturn(new RiotMatchSyncService.SyncResult(1, 0, 0, 0, List.of()));
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

    @Test
    void 모든_디비전의_현재_페이지가_비어_있으면_첫_페이지로_돌아간다() {
        properties.setDivisions(List.of("IV"));
        orchestrator.runOnce();
        orchestrator.runOnce();
        orchestrator.runOnce();
        orchestrator.runOnce();
        orchestrator.runOnce();

        verify(playerSyncService, times(2))
                .syncLeagueEntries("RANKED_SOLO_5x5", "PLATINUM", "IV", 1);
        verify(playerSyncService, never())
                .syncLeagueEntries("RANKED_SOLO_5x5", "PLATINUM", "IV", 2);
    }

    private NormalizedMatch normalizedMatch(String matchId) {
        return new NormalizedMatch(matchId, "16.15", 420, List.of());
    }

}
