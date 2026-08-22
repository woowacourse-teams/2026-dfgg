package dfgg.application.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dfgg.application.MatchParticipantCohortPersistenceService;
import dfgg.domain.player.Player;
import dfgg.domain.player.PlayerRepository;
import dfgg.infrastructure.external.client.RiotClient;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RiotMatchSyncServiceTest {

    @Mock
    private RiotClient riotClient;

    @Mock
    private RawMatchService rawMatchService;

    @Mock
    private RawMatchTimelineService rawMatchTimelineService;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private MatchParticipantCohortPersistenceService cohortPersistenceService;

    @InjectMocks
    private RiotMatchSyncService riotMatchSyncService;

    @Test
    void 플레이어의_매치_ID를_중복_제거하고_새로운_매치만_저장한다() {
        when(riotClient.getMatchIds("puuid-1", 0, 20))
                .thenReturn(List.of("KR_1", "KR_2"));
        when(riotClient.getMatchIds("puuid-2", 0, 20))
                .thenReturn(List.of("KR_2", "KR_3"));
        when(rawMatchService.findExistingMatchIds(
                new LinkedHashSet<>(List.of("KR_1", "KR_2", "KR_3"))
        )).thenReturn(Set.of("KR_2"));
        when(rawMatchTimelineService.findExistingMatchIds(
                new LinkedHashSet<>(List.of("KR_1", "KR_2", "KR_3"))
        )).thenReturn(Set.of("KR_2"));

        riotMatchSyncService.syncMatches(List.of("puuid-1", "puuid-2"), 0, 20);

        verify(rawMatchService, never()).collectRawMatch("KR_2");
        verify(rawMatchService).collectRawMatch("KR_1");
        verify(rawMatchService).collectRawMatch("KR_3");
        verify(rawMatchTimelineService).collectRawMatchTimeline("KR_1");
        verify(rawMatchTimelineService).collectRawMatchTimeline("KR_3");
    }

    @Test
    void 수집할_플레이어가_없으면_Riot_API를_호출하지_않는다() {
        riotMatchSyncService.syncMatches(List.of(), 0, 20);

        verifyNoInteractions(
                riotClient,
                rawMatchService,
                rawMatchTimelineService
        );
    }

    @Test
    void 이미_저장된_매치는_상세_API를_호출하지_않는다() {
        when(riotClient.getMatchIds("puuid-1", 0, 20)).thenReturn(List.of("KR_1"));
        when(rawMatchService.findExistingMatchIds(Set.of("KR_1"))).thenReturn(Set.of("KR_1"));
        when(rawMatchTimelineService.findExistingMatchIds(Set.of("KR_1")))
                .thenReturn(Set.of("KR_1"));

        riotMatchSyncService.syncMatches(List.of("puuid-1"), 0, 20);

        verify(rawMatchService, never()).collectRawMatch(any());
        verify(rawMatchTimelineService, never()).collectRawMatchTimeline(any());
    }

    @Test
    void 상세_원본만_저장된_매치는_Timeline만_보완한다() {
        when(riotClient.getMatchIds("puuid-1", 0, 20)).thenReturn(List.of("KR_1"));
        when(rawMatchService.findExistingMatchIds(Set.of("KR_1"))).thenReturn(Set.of("KR_1"));
        when(rawMatchTimelineService.findExistingMatchIds(Set.of("KR_1"))).thenReturn(Set.of());

        riotMatchSyncService.syncMatches(List.of("puuid-1"), 0, 20);

        verify(rawMatchService, never()).collectRawMatch(any());
        verify(rawMatchTimelineService).collectRawMatchTimeline("KR_1");
    }

    @Test
    void 한_매치가_실패해도_다음_매치를_계속_수집한다() {
        when(riotClient.getMatchIds("puuid-1", 0, 2)).thenReturn(List.of("KR_1", "KR_2"));
        when(rawMatchService.findExistingMatchIds(Set.of("KR_1", "KR_2"))).thenReturn(Set.of());
        when(rawMatchTimelineService.findExistingMatchIds(Set.of("KR_1", "KR_2"))).thenReturn(Set.of());
        when(rawMatchService.collectRawMatch("KR_1"))
                .thenThrow(new IllegalStateException("match failed"));
        when(rawMatchService.collectRawMatch("KR_2")).thenReturn(true);
        when(rawMatchTimelineService.collectRawMatchTimeline("KR_2")).thenReturn(true);

        RiotMatchSyncService.SyncResult result = riotMatchSyncService.syncMatches(
                List.of("puuid-1"),
                0,
                2
        );

        assertThat(result.newMatches()).isEqualTo(1);
        assertThat(result.newTimelines()).isEqualTo(1);
        assertThat(result.failures())
                .extracting(RiotMatchSyncService.Failure::stage, RiotMatchSyncService.Failure::targetId)
                .containsExactly(tuple("MATCH", "KR_1"));
        verify(rawMatchService).collectRawMatch("KR_2");
    }

    @Test
    void 실패한_Timeline은_다음_실행에서_다시_수집한다() {
        when(riotClient.getMatchIds("puuid-1", 0, 1)).thenReturn(List.of("KR_1"));
        when(rawMatchService.findExistingMatchIds(Set.of("KR_1")))
                .thenReturn(Set.of(), Set.of("KR_1"));
        when(rawMatchTimelineService.findExistingMatchIds(Set.of("KR_1"))).thenReturn(Set.of());
        when(rawMatchService.collectRawMatch("KR_1")).thenReturn(true);
        when(rawMatchTimelineService.collectRawMatchTimeline("KR_1"))
                .thenThrow(new IllegalStateException("temporary failure"))
                .thenReturn(true);

        RiotMatchSyncService.SyncResult first = riotMatchSyncService.syncMatches(
                List.of("puuid-1"),
                0,
                1
        );
        RiotMatchSyncService.SyncResult second = riotMatchSyncService.syncMatches(
                List.of("puuid-1"),
                0,
                1
        );

        assertThat(first.failures()).hasSize(1);
        assertThat(first.newTimelines()).isZero();
        assertThat(second.failures()).isEmpty();
        assertThat(second.newTimelines()).isEqualTo(1);
        verify(rawMatchService, times(1)).collectRawMatch("KR_1");
        verify(rawMatchTimelineService, times(2)).collectRawMatchTimeline("KR_1");
    }

    @Test
    void 기존_원본_중_Timeline이_없는_매치만_보완한다() {
        when(rawMatchTimelineService.collectMissingTimelines()).thenReturn(
                new RawMatchTimelineService.MissingTimelineSyncResult(1, 0, List.of())
        );

        int persisted = riotMatchSyncService.syncMissingTimelines();

        assertThat(persisted).isEqualTo(1);
        verify(rawMatchTimelineService).collectMissingTimelines();
    }

    @Test
    void 매치_수집_당시_PUUID와_티어를_매치에_연결한다() {
        Player player = new Player(
                "puuid-1",
                "KR",
                "PLATINUM",
                "I",
                Instant.parse("2026-08-22T00:00:00Z")
        );
        when(playerRepository.findAllById(List.of("puuid-1"))).thenReturn(List.of(player));
        when(riotClient.getMatchIds("puuid-1", 0, 1)).thenReturn(List.of("KR_1"));
        when(rawMatchService.findExistingMatchIds(Set.of("KR_1"))).thenReturn(Set.of());
        when(rawMatchTimelineService.findExistingMatchIds(Set.of("KR_1"))).thenReturn(Set.of());

        riotMatchSyncService.syncMatches(List.of("puuid-1"), 0, 1);

        verify(cohortPersistenceService).persist(argThat(saved ->
                saved.getMatchId().equals("KR_1")
                        && saved.getPuuid().equals("puuid-1")
                        && saved.getTier().equals("PLATINUM")));
    }

    @Test
    void 이번_리그_조회에서_전달한_PUUID만_최신_범위부터_수집한다() {
        when(riotClient.getMatchIds("puuid-current", 0, 20)).thenReturn(List.of("KR_1"));
        when(rawMatchService.findExistingMatchIds(Set.of("KR_1"))).thenReturn(Set.of());
        when(rawMatchTimelineService.findExistingMatchIds(Set.of("KR_1"))).thenReturn(Set.of());
        when(rawMatchService.collectRawMatch("KR_1")).thenReturn(true);
        when(rawMatchTimelineService.collectRawMatchTimeline("KR_1")).thenReturn(true);

        riotMatchSyncService.syncMatches(List.of("puuid-current"), 0, 20);

        verify(riotClient).getMatchIds("puuid-current", 0, 20);
    }

    @Test
    void 한_PUUID의_실패가_다른_PUUID의_수집을_막지_않는다() {
        when(riotClient.getMatchIds("puuid-failing", 0, 20))
                .thenThrow(new IllegalStateException("temporary failure"));
        when(riotClient.getMatchIds("puuid-succeeding", 0, 20)).thenReturn(List.of());

        RiotMatchSyncService.SyncResult result = riotMatchSyncService.syncMatches(
                List.of("puuid-failing", "puuid-succeeding"),
                0,
                20
        );

        assertThat(result.failures())
                .extracting(RiotMatchSyncService.Failure::stage, RiotMatchSyncService.Failure::targetId)
                .containsExactly(tuple("MATCH_IDS", "puuid-failing"));
        verify(riotClient).getMatchIds("puuid-succeeding", 0, 20);
    }

    @Test
    void 같은_매치를_반환한_PUUID들은_원본과_Timeline을_한_번만_저장한다() {
        when(riotClient.getMatchIds("puuid-1", 0, 20)).thenReturn(List.of("KR_SHARED"));
        when(riotClient.getMatchIds("puuid-2", 0, 20)).thenReturn(List.of("KR_SHARED"));
        when(rawMatchService.findExistingMatchIds(Set.of("KR_SHARED"))).thenReturn(Set.of());
        when(rawMatchTimelineService.findExistingMatchIds(Set.of("KR_SHARED"))).thenReturn(Set.of());
        when(rawMatchService.collectRawMatch("KR_SHARED")).thenReturn(true);
        when(rawMatchTimelineService.collectRawMatchTimeline("KR_SHARED")).thenReturn(true);

        riotMatchSyncService.syncMatches(List.of("puuid-1", "puuid-2"), 0, 20);

        verify(rawMatchService, times(1)).collectRawMatch("KR_SHARED");
        verify(rawMatchTimelineService, times(1)).collectRawMatchTimeline("KR_SHARED");
    }

    @Test
    void 다시_실행하면_최신_범위를_재조회하고_저장된_매치_상세는_건너뛴다() {
        when(riotClient.getMatchIds("puuid-1", 0, 20)).thenReturn(List.of("KR_1"));
        when(rawMatchService.findExistingMatchIds(Set.of("KR_1")))
                .thenReturn(Set.of(), Set.of("KR_1"));
        when(rawMatchTimelineService.findExistingMatchIds(Set.of("KR_1")))
                .thenReturn(Set.of(), Set.of("KR_1"));
        when(rawMatchService.collectRawMatch("KR_1")).thenReturn(true);
        when(rawMatchTimelineService.collectRawMatchTimeline("KR_1")).thenReturn(true);

        riotMatchSyncService.syncMatches(List.of("puuid-1"), 0, 20);
        riotMatchSyncService.syncMatches(List.of("puuid-1"), 0, 20);

        verify(riotClient, times(2)).getMatchIds("puuid-1", 0, 20);
        verify(rawMatchService, times(1)).collectRawMatch("KR_1");
        verify(rawMatchTimelineService, times(1)).collectRawMatchTimeline("KR_1");
    }

}
