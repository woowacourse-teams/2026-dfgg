package dfgg.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

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
        properties.setPlayerLimit(100);
        properties.setRecoverMissingTimelines(true);
        when(playerSyncService.syncLeagueEntries(
                anyString(), anyString(), anyString(), anyInt()
        )).thenReturn(new RiotPlayerSyncService.SyncResult(0, List.of()));
        when(matchSyncService.findMatchIds(anyString(), anyInt(), anyInt()))
                .thenReturn(List.of());

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
        when(matchSyncService.findMatchIds("puuid-1", 0, 20))
                .thenReturn(List.of("KR_1"));
        when(matchSyncService.syncMatch("KR_1")).thenReturn(true);
        NormalizedMatch normalized = normalizedMatch("KR_1");
        when(matchNormalizationService.normalizeAsTierSample("KR_1", "PLATINUM")).thenReturn(normalized);
        orchestrator.runOnce();

        InOrder order = inOrder(
                playerSyncService,
                matchSyncService,
                matchNormalizationService,
                statsMatchService
        );
        order.verify(playerSyncService)
                .syncLeagueEntries("RANKED_SOLO_5x5", "PLATINUM", "I", 1);
        order.verify(matchSyncService).findMatchIds("puuid-1", 0, 20);
        order.verify(matchSyncService).syncMatch("KR_1");
        order.verify(matchNormalizationService).normalizeAsTierSample("KR_1", "PLATINUM");
        order.verify(matchNormalizationService).save(normalized);
        order.verify(statsMatchService).registerMatchStats(normalized, "PLATINUM");
        order.verify(matchSyncService).findMatchIds("puuid-2", 0, 20);
        order.verify(matchSyncService).syncMissingTimelines();
    }

    @Test
    void 여러_플레이어가_같은_매치를_조회해도_한_번만_끝까지_처리한다() {
        when(playerSyncService.syncLeagueEntries("RANKED_SOLO_5x5", "PLATINUM", "I", 1))
                .thenReturn(new RiotPlayerSyncService.SyncResult(
                        2,
                        List.of("puuid-1", "puuid-2")
                ));
        when(matchSyncService.findMatchIds(anyString(), eq(0), eq(20)))
                .thenReturn(List.of("KR_SHARED"));
        when(matchSyncService.syncMatch("KR_SHARED")).thenReturn(true);
        NormalizedMatch normalized = normalizedMatch("KR_SHARED");
        when(matchNormalizationService.normalizeAsTierSample("KR_SHARED", "PLATINUM")).thenReturn(normalized);
        orchestrator.runOnce();

        verify(matchSyncService, times(2)).findMatchIds(anyString(), eq(0), eq(20));
        verify(matchSyncService).syncMatch("KR_SHARED");
        verify(matchNormalizationService).normalizeAsTierSample("KR_SHARED", "PLATINUM");
        verify(matchNormalizationService).save(normalized);
        verify(statsMatchService).registerMatchStats(normalized, "PLATINUM");
    }

    @Test
    void 설정한_플레이어_수까지만_매치를_수집한다() {
        properties.setPlayerLimit(1);
        properties.setMatchCount(47);
        when(playerSyncService.syncLeagueEntries("RANKED_SOLO_5x5", "PLATINUM", "I", 1))
                .thenReturn(new RiotPlayerSyncService.SyncResult(
                        2,
                        List.of("puuid-1", "puuid-2")
                ));

        orchestrator.runOnce();

        verify(matchSyncService).findMatchIds("puuid-1", 0, 47);
        verify(matchSyncService, never()).findMatchIds("puuid-2", 0, 47);
    }

    @Test
    void 자동_Timeline_복구가_꺼져_있으면_누락_Timeline을_조회하지_않는다() {
        properties.setRecoverMissingTimelines(false);

        orchestrator.runOnce();

        verify(matchSyncService, never()).syncMissingTimelines();
    }

    @Test
    void 스케줄러는_기존_미처리_매치를_자동_재집계하지_않는다() {
        when(matchNormalizationService.findPendingMatchIdsAfter(""))
                .thenReturn(List.of("KR_PENDING"));

        orchestrator.runOnce();

        verify(matchNormalizationService, never()).findPendingMatchIdsAfter(anyString());
        verify(matchNormalizationService, never())
                .normalizeAsTierSample("KR_PENDING", "PLATINUM");
        verifyNoInteractions(statsMatchService);
    }

    @Test
    void 관리자_재집계는_Riot_API_갱신_없이_표본_티어를_사용한다() {
        NormalizedMatch normalized = normalizedMatch("KR_1");
        when(matchNormalizationService.findPendingMatchIdsAfter(""))
                .thenReturn(List.of("KR_1"));
        when(matchNormalizationService.findPendingMatchIdsAfter("KR_1"))
                .thenReturn(List.of());
        when(matchNormalizationService.normalizeAsTierSample("KR_1", "PLATINUM"))
                .thenReturn(normalized);

        orchestrator.normalizeAndAggregatePendingMatches("PLATINUM");

        InOrder order = inOrder(matchNormalizationService, statsMatchService);
        order.verify(matchNormalizationService).normalizeAsTierSample("KR_1", "PLATINUM");
        order.verify(matchNormalizationService).save(normalized);
        order.verify(statsMatchService).registerMatchStats(normalized, "PLATINUM");
        verify(matchNormalizationService, never()).normalize("KR_1");
        verifyNoInteractions(playerSyncService, matchSyncService);
    }

    @Test
    void 관리자_재집계에서_실패한_매치는_전체_조회_후_한_번_재시도한다() {
        NormalizedMatch retried = normalizedMatch("KR_RETRY");
        NormalizedMatch succeeded = normalizedMatch("KR_SUCCEEDED");
        when(matchNormalizationService.findPendingMatchIdsAfter(""))
                .thenReturn(List.of("KR_RETRY", "KR_SUCCEEDED"));
        when(matchNormalizationService.findPendingMatchIdsAfter("KR_SUCCEEDED"))
                .thenReturn(List.of());
        when(matchNormalizationService.normalizeAsTierSample("KR_RETRY", "PLATINUM"))
                .thenThrow(new IllegalStateException("temporary Riot failure"))
                .thenReturn(retried);
        when(matchNormalizationService.normalizeAsTierSample("KR_SUCCEEDED", "PLATINUM"))
                .thenReturn(succeeded);

        orchestrator.normalizeAndAggregatePendingMatches("PLATINUM");

        InOrder order = inOrder(matchNormalizationService, statsMatchService);
        order.verify(matchNormalizationService).normalizeAsTierSample("KR_RETRY", "PLATINUM");
        order.verify(matchNormalizationService).normalizeAsTierSample("KR_SUCCEEDED", "PLATINUM");
        order.verify(matchNormalizationService).save(succeeded);
        order.verify(statsMatchService).registerMatchStats(succeeded, "PLATINUM");
        order.verify(matchNormalizationService).normalizeAsTierSample("KR_RETRY", "PLATINUM");
        order.verify(matchNormalizationService).save(retried);
        order.verify(statsMatchService).registerMatchStats(retried, "PLATINUM");
    }

    @Test
    void 관리자_재집계의_재시도도_실패하면_원래_원인을_보존한다() {
        IllegalStateException failure = new IllegalStateException("Riot rate limit");
        when(matchNormalizationService.findPendingMatchIdsAfter(""))
                .thenReturn(List.of("KR_FAILED"));
        when(matchNormalizationService.findPendingMatchIdsAfter("KR_FAILED"))
                .thenReturn(List.of());
        when(matchNormalizationService.normalizeAsTierSample("KR_FAILED", "PLATINUM"))
                .thenThrow(failure);

        assertThatThrownBy(() -> orchestrator.normalizeAndAggregatePendingMatches("PLATINUM"))
                .isInstanceOf(IllegalStateException.class)
                .hasCause(failure);

        verify(matchNormalizationService, times(2))
                .normalizeAsTierSample("KR_FAILED", "PLATINUM");
        verifyNoInteractions(statsMatchService);
    }

    @Test
    void Retry_After가_없는_429는_매치를_재시도하지_않는다() {
        HttpClientErrorException rateLimit = new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS);
        when(matchNormalizationService.findPendingMatchIdsAfter(""))
                .thenReturn(List.of("KR_RATE_LIMITED"));
        when(matchNormalizationService.findPendingMatchIdsAfter("KR_RATE_LIMITED"))
                .thenReturn(List.of());
        when(matchNormalizationService.normalizeAsTierSample("KR_RATE_LIMITED", "PLATINUM"))
                .thenThrow(rateLimit);

        assertThatThrownBy(() -> orchestrator.normalizeAndAggregatePendingMatches("PLATINUM"))
                .isInstanceOf(IllegalStateException.class)
                .hasCause(rateLimit);

        verify(matchNormalizationService).normalizeAsTierSample("KR_RATE_LIMITED", "PLATINUM");
        verifyNoInteractions(statsMatchService);
    }

    @Test
    void 관리자_재집계에서_통계_집계가_실패하면_한_번_재시도한다() {
        NormalizedMatch normalized = normalizedMatch("KR_STATS_RETRY");
        when(matchNormalizationService.findPendingMatchIdsAfter(""))
                .thenReturn(List.of("KR_STATS_RETRY"));
        when(matchNormalizationService.findPendingMatchIdsAfter("KR_STATS_RETRY"))
                .thenReturn(List.of());
        when(matchNormalizationService.normalizeAsTierSample("KR_STATS_RETRY", "PLATINUM"))
                .thenReturn(normalized);
        doThrow(new IllegalStateException("temporary database failure"))
                .doNothing()
                .when(statsMatchService)
                .registerMatchStats(normalized, "PLATINUM");

        orchestrator.normalizeAndAggregatePendingMatches("PLATINUM");

        verify(matchNormalizationService).normalizeAsTierSample("KR_STATS_RETRY", "PLATINUM");
        verify(matchNormalizationService).save(normalized);
        verify(statsMatchService, times(2)).registerMatchStats(normalized, "PLATINUM");
    }

    @Test
    void 한_매치의_정규화_저장이_실패해도_다음_매치와_통계_집계를_계속한다() {
        NormalizedMatch failed = normalizedMatch("KR_FAILED");
        NormalizedMatch succeeded = normalizedMatch("KR_SUCCEEDED");
        when(playerSyncService.syncLeagueEntries("RANKED_SOLO_5x5", "PLATINUM", "I", 1))
                .thenReturn(new RiotPlayerSyncService.SyncResult(1, List.of("puuid-1")));
        when(matchSyncService.findMatchIds("puuid-1", 0, 20))
                .thenReturn(List.of("KR_FAILED", "KR_SUCCEEDED"));
        when(matchSyncService.syncMatch("KR_FAILED")).thenReturn(true);
        when(matchSyncService.syncMatch("KR_SUCCEEDED")).thenReturn(true);
        when(matchNormalizationService.normalizeAsTierSample("KR_FAILED", "PLATINUM"))
                .thenReturn(failed);
        when(matchNormalizationService.normalizeAsTierSample("KR_SUCCEEDED", "PLATINUM"))
                .thenReturn(succeeded);
        doThrow(new IllegalStateException("database unavailable"))
                .when(matchNormalizationService).save(failed);
        orchestrator.runOnce();

        InOrder order = inOrder(matchNormalizationService, statsMatchService);
        order.verify(matchNormalizationService).normalizeAsTierSample("KR_FAILED", "PLATINUM");
        order.verify(matchNormalizationService).save(failed);
        order.verify(matchNormalizationService).normalizeAsTierSample("KR_SUCCEEDED", "PLATINUM");
        order.verify(matchNormalizationService).save(succeeded);
        order.verify(statsMatchService).registerMatchStats(succeeded, "PLATINUM");
        verify(statsMatchService, never()).registerMatchStats(failed, "PLATINUM");
    }

    @Test
    void 한_매치의_통계_집계가_실패해도_다음_매치의_정규화와_통계_집계를_계속한다() {
        NormalizedMatch failed = normalizedMatch("KR_FAILED");
        NormalizedMatch succeeded = normalizedMatch("KR_SUCCEEDED");
        when(playerSyncService.syncLeagueEntries("RANKED_SOLO_5x5", "PLATINUM", "I", 1))
                .thenReturn(new RiotPlayerSyncService.SyncResult(1, List.of("puuid-1")));
        when(matchSyncService.findMatchIds("puuid-1", 0, 20))
                .thenReturn(List.of("KR_FAILED", "KR_SUCCEEDED"));
        when(matchSyncService.syncMatch("KR_FAILED")).thenReturn(true);
        when(matchSyncService.syncMatch("KR_SUCCEEDED")).thenReturn(true);
        when(matchNormalizationService.normalizeAsTierSample("KR_FAILED", "PLATINUM"))
                .thenReturn(failed);
        when(matchNormalizationService.normalizeAsTierSample("KR_SUCCEEDED", "PLATINUM"))
                .thenReturn(succeeded);
        doThrow(new IllegalStateException("statistics unavailable"))
                .when(statsMatchService).registerMatchStats(failed, "PLATINUM");

        orchestrator.runOnce();

        InOrder order = inOrder(matchNormalizationService, statsMatchService);
        order.verify(matchNormalizationService).normalizeAsTierSample("KR_FAILED", "PLATINUM");
        order.verify(matchNormalizationService).save(failed);
        order.verify(statsMatchService).registerMatchStats(failed, "PLATINUM");
        order.verify(matchNormalizationService).normalizeAsTierSample("KR_SUCCEEDED", "PLATINUM");
        order.verify(matchNormalizationService).save(succeeded);
        order.verify(statsMatchService).registerMatchStats(succeeded, "PLATINUM");
    }

    @Test
    void 한_단계가_실패해도_후속_단계를_계속한다() {
        when(playerSyncService.syncLeagueEntries("RANKED_SOLO_5x5", "PLATINUM", "I", 1))
                .thenThrow(new IllegalStateException("league unavailable"));
        orchestrator.runOnce();
        verify(matchSyncService, never()).findMatchIds(anyString(), eq(0), eq(20));
        verify(matchSyncService).syncMissingTimelines();
        verifyNoInteractions(statsMatchService);
    }

    @Test
    void 스케줄이_성공할_때마다_다음_리그_페이지의_PUUID만_매치_수집에_전달한다() {
        when(playerSyncService.syncLeagueEntries("RANKED_SOLO_5x5", "PLATINUM", "I", 1))
                .thenReturn(new RiotPlayerSyncService.SyncResult(1, List.of("puuid-1")));
        when(playerSyncService.syncLeagueEntries("RANKED_SOLO_5x5", "PLATINUM", "I", 2))
                .thenReturn(new RiotPlayerSyncService.SyncResult(1, List.of("puuid-2")));
        orchestrator.runOnce();
        orchestrator.runOnce();

        verify(playerSyncService)
                .syncLeagueEntries("RANKED_SOLO_5x5", "PLATINUM", "I", 1);
        verify(playerSyncService)
                .syncLeagueEntries("RANKED_SOLO_5x5", "PLATINUM", "I", 2);
        verify(matchSyncService).findMatchIds("puuid-1", 0, 20);
        verify(matchSyncService).findMatchIds("puuid-2", 0, 20);
    }

    @Test
    void 수집이_실패하면_해당_범위를_다음_스케줄에서_다시_시도한다() {
        when(playerSyncService.syncLeagueEntries("RANKED_SOLO_5x5", "PLATINUM", "I", 1))
                .thenThrow(new IllegalStateException("league unavailable"));
        orchestrator.runOnce();
        orchestrator.runOnce();

        verify(playerSyncService, times(2))
                .syncLeagueEntries("RANKED_SOLO_5x5", "PLATINUM", "I", 1);
        verify(matchSyncService, never()).findMatchIds(anyString(), eq(0), eq(20));
    }

    @Test
    void 시작_디비전부터_I까지_진행한_뒤_다음_리그_페이지로_이동한다() {
        properties.setDivisions(List.of("IV"));
        when(playerSyncService.syncLeagueEntries(
                anyString(), anyString(), anyString(), anyInt()
        )).thenReturn(new RiotPlayerSyncService.SyncResult(0, List.of("puuid-1")));
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
