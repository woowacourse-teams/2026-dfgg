package dfgg.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dfgg.domain.match.RawMatch;
import dfgg.domain.match.RawMatchRepository;
import dfgg.domain.match.RawMatchTimeline;
import dfgg.domain.match.RawMatchTimelineRepository;
import dfgg.domain.player.PlayerRepository;
import dfgg.domain.player.PlayerCohortRepository;
import dfgg.infrastructure.external.client.RiotClient;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class RiotMatchSyncServiceTest {

    @Mock
    private RiotClient riotClient;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private RawMatchRepository rawMatchRepository;

    @Mock
    private RawMatchPersistenceService persistenceService;

    @Mock
    private RawMatchTimelineRepository rawMatchTimelineRepository;

    @Mock
    private RawMatchTimelinePersistenceService timelinePersistenceService;

    @Mock
    private PlayerCohortRepository playerCohortRepository;

    @Mock
    private MatchParticipantCohortPersistenceService cohortPersistenceService;

    @InjectMocks
    private RiotMatchSyncService riotMatchSyncService;

    @Test
    void 플레이어의_매치_ID를_중복_제거하고_새로운_매치만_저장한다() {
        when(playerRepository.findPuuidsByPlatform("KR", PageRequest.of(1, 10)))
                .thenReturn(List.of("puuid-1", "puuid-2"));
        when(riotClient.getMatchIds("puuid-1", 0, 20))
                .thenReturn(List.of("KR_1", "KR_2"));
        when(riotClient.getMatchIds("puuid-2", 0, 20))
                .thenReturn(List.of("KR_2", "KR_3"));
        when(rawMatchRepository.findExistingMatchIds(
                new LinkedHashSet<>(List.of("KR_1", "KR_2", "KR_3"))
        )).thenReturn(Set.of("KR_2"));
        when(rawMatchTimelineRepository.findExistingMatchIds(
                new LinkedHashSet<>(List.of("KR_1", "KR_2", "KR_3"))
        )).thenReturn(Set.of("KR_2"));
        when(riotClient.getRawMatch("KR_1")).thenReturn("{\"match\":1}");
        when(riotClient.getRawMatch("KR_3")).thenReturn("{\"match\":3}");
        when(riotClient.getRawMatchTimeline("KR_1")).thenReturn("{\"timeline\":1}");
        when(riotClient.getRawMatchTimeline("KR_3")).thenReturn("{\"timeline\":3}");

        riotMatchSyncService.syncMatches(1, 10, 0, 20);

        verify(riotClient, never()).getRawMatch("KR_2");
        ArgumentCaptor<RawMatch> rawMatchCaptor = ArgumentCaptor.forClass(RawMatch.class);
        verify(persistenceService, times(2)).persist(rawMatchCaptor.capture());
        assertThat(rawMatchCaptor.getAllValues())
                .extracting(RawMatch::getMatchId, RawMatch::getRawData)
                .containsExactly(
                        tuple("KR_1", "{\"match\":1}"),
                        tuple("KR_3", "{\"match\":3}")
                );
        ArgumentCaptor<RawMatchTimeline> timelineCaptor = ArgumentCaptor.forClass(RawMatchTimeline.class);
        verify(timelinePersistenceService, times(2)).persist(timelineCaptor.capture());
        assertThat(timelineCaptor.getAllValues())
                .extracting(RawMatchTimeline::getMatchId, RawMatchTimeline::getRawData)
                .containsExactly(
                        tuple("KR_1", "{\"timeline\":1}"),
                        tuple("KR_3", "{\"timeline\":3}")
                );
    }

    @Test
    void 저장된_플레이어가_없으면_Riot_API를_호출하지_않는다() {
        when(playerRepository.findPuuidsByPlatform("KR", PageRequest.of(0, 20)))
                .thenReturn(List.of());

        riotMatchSyncService.syncMatches(0, 20, 0, 20);

        verifyNoInteractions(
                riotClient,
                rawMatchRepository,
                rawMatchTimelineRepository,
                persistenceService,
                timelinePersistenceService
        );
    }

    @Test
    void 이미_저장된_매치는_상세_API를_호출하지_않는다() {
        when(playerRepository.findPuuidsByPlatform("KR", PageRequest.of(0, 20)))
                .thenReturn(List.of("puuid-1"));
        when(riotClient.getMatchIds("puuid-1", 0, 20)).thenReturn(List.of("KR_1"));
        when(rawMatchRepository.findExistingMatchIds(Set.of("KR_1"))).thenReturn(Set.of("KR_1"));
        when(rawMatchTimelineRepository.findExistingMatchIds(Set.of("KR_1")))
                .thenReturn(Set.of("KR_1"));

        riotMatchSyncService.syncMatches(0, 20, 0, 20);

        verify(riotClient, never()).getRawMatch(any());
        verify(riotClient, never()).getRawMatchTimeline(any());
        verifyNoInteractions(persistenceService);
        verifyNoInteractions(timelinePersistenceService);
    }

    @Test
    void 상세_원본만_저장된_매치는_Timeline만_보완한다() {
        when(playerRepository.findPuuidsByPlatform("KR", PageRequest.of(0, 20)))
                .thenReturn(List.of("puuid-1"));
        when(riotClient.getMatchIds("puuid-1", 0, 20)).thenReturn(List.of("KR_1"));
        when(rawMatchRepository.findExistingMatchIds(Set.of("KR_1"))).thenReturn(Set.of("KR_1"));
        when(rawMatchTimelineRepository.findExistingMatchIds(Set.of("KR_1"))).thenReturn(Set.of());
        when(riotClient.getRawMatchTimeline("KR_1")).thenReturn("{\"timeline\":1}");

        riotMatchSyncService.syncMatches(0, 20, 0, 20);

        verify(riotClient, never()).getRawMatch(any());
        verify(riotClient).getRawMatchTimeline("KR_1");
        verifyNoInteractions(persistenceService);
        verify(timelinePersistenceService).persist(any());
    }

    @Test
    void 한_매치가_실패해도_다음_매치를_계속_수집한다() {
        when(playerRepository.findPuuidsByPlatform("KR", PageRequest.of(0, 1)))
                .thenReturn(List.of("puuid-1"));
        when(riotClient.getMatchIds("puuid-1", 0, 2)).thenReturn(List.of("KR_1", "KR_2"));
        when(rawMatchRepository.findExistingMatchIds(Set.of("KR_1", "KR_2"))).thenReturn(Set.of());
        when(rawMatchTimelineRepository.findExistingMatchIds(Set.of("KR_1", "KR_2"))).thenReturn(Set.of());
        when(riotClient.getRawMatch("KR_1")).thenThrow(new IllegalStateException("match failed"));
        when(riotClient.getRawMatch("KR_2")).thenReturn("{\"match\":2}");
        when(riotClient.getRawMatchTimeline("KR_2")).thenReturn("{\"timeline\":2}");
        when(persistenceService.persist(any())).thenReturn(true);
        when(timelinePersistenceService.persist(any())).thenReturn(true);

        RiotMatchSyncService.SyncResult result = riotMatchSyncService.syncMatches(0, 1, 0, 2);

        assertThat(result.newMatches()).isEqualTo(1);
        assertThat(result.newTimelines()).isEqualTo(1);
        assertThat(result.failures())
                .extracting(RiotMatchSyncService.Failure::stage, RiotMatchSyncService.Failure::targetId)
                .containsExactly(tuple("MATCH", "KR_1"));
        verify(riotClient).getRawMatch("KR_2");
    }

    @Test
    void 실패한_Timeline은_다음_실행에서_다시_수집한다() {
        when(playerRepository.findPuuidsByPlatform("KR", PageRequest.of(0, 1)))
                .thenReturn(List.of("puuid-1"));
        when(riotClient.getMatchIds("puuid-1", 0, 1)).thenReturn(List.of("KR_1"));
        when(rawMatchRepository.findExistingMatchIds(Set.of("KR_1")))
                .thenReturn(Set.of(), Set.of("KR_1"));
        when(rawMatchTimelineRepository.findExistingMatchIds(Set.of("KR_1"))).thenReturn(Set.of());
        when(riotClient.getRawMatch("KR_1")).thenReturn("{\"match\":1}");
        when(persistenceService.persist(any())).thenReturn(true);
        when(riotClient.getRawMatchTimeline("KR_1"))
                .thenThrow(new IllegalStateException("temporary failure"))
                .thenReturn("{\"timeline\":1}");
        when(timelinePersistenceService.persist(any())).thenReturn(true);

        RiotMatchSyncService.SyncResult first = riotMatchSyncService.syncMatches(0, 1, 0, 1);
        RiotMatchSyncService.SyncResult second = riotMatchSyncService.syncMatches(0, 1, 0, 1);

        assertThat(first.failures()).hasSize(1);
        assertThat(first.newTimelines()).isZero();
        assertThat(second.failures()).isEmpty();
        assertThat(second.newTimelines()).isEqualTo(1);
        verify(riotClient, times(1)).getRawMatch("KR_1");
        verify(riotClient, times(2)).getRawMatchTimeline("KR_1");
    }

    @Test
    void 기존_원본_중_Timeline이_없는_매치만_보완한다() {
        when(rawMatchRepository.findMatchIdsMissingTimelineAfter(
                "", PageRequest.of(0, 100)
        )).thenReturn(List.of("KR_2"));
        when(rawMatchRepository.findMatchIdsMissingTimelineAfter(
                "KR_2", PageRequest.of(0, 100)
        )).thenReturn(List.of());
        when(riotClient.getRawMatchTimeline("KR_2")).thenReturn("{\"timeline\":2}");
        when(timelinePersistenceService.persist(any())).thenReturn(true);

        int persisted = riotMatchSyncService.syncMissingTimelines();

        assertThat(persisted).isEqualTo(1);
        verify(riotClient).getRawMatchTimeline("KR_2");
        verify(timelinePersistenceService).persist(argThat(timeline ->
                timeline.getMatchId().equals("KR_2")
                        && timeline.getRawData().equals("{\"timeline\":2}")));
    }

    @Test
    void Timeline_저장_경합은_건너뛴_항목으로_집계한다() {
        when(rawMatchRepository.findMatchIdsMissingTimelineAfter(
                "", PageRequest.of(0, 100)
        )).thenReturn(List.of("KR_2"));
        when(rawMatchRepository.findMatchIdsMissingTimelineAfter(
                "KR_2", PageRequest.of(0, 100)
        )).thenReturn(List.of());
        when(riotClient.getRawMatchTimeline("KR_2")).thenReturn("{\"timeline\":2}");
        when(timelinePersistenceService.persist(any())).thenReturn(false);

        RiotMatchSyncService.SyncResult result = riotMatchSyncService.syncMissingTimelinesWithResult();

        assertThat(result.newTimelines()).isZero();
        assertThat(result.skippedItems()).isEqualTo(1);
        assertThat(result.failures()).isEmpty();
    }

    @Test
    void 매치_수집_당시_PUUID와_티어를_매치에_연결한다() {
        PlayerCohortRepository.Target cohort = mock(PlayerCohortRepository.Target.class);
        when(cohort.getPuuid()).thenReturn("puuid-1");
        when(cohort.getQueueType()).thenReturn("RANKED_SOLO_5x5");
        when(cohort.getTier()).thenReturn("PLATINUM");
        when(cohort.getDivision()).thenReturn("I");
        when(playerRepository.findPuuidsByPlatform("KR", PageRequest.of(0, 1)))
                .thenReturn(List.of("puuid-1"));
        when(playerCohortRepository.findTargetsByPuuidsAndQueueType(
                List.of("puuid-1"), "RANKED_SOLO_5x5"))
                .thenReturn(List.of(cohort));
        when(riotClient.getMatchIds("puuid-1", 0, 1)).thenReturn(List.of("KR_1"));
        when(rawMatchRepository.findExistingMatchIds(Set.of("KR_1"))).thenReturn(Set.of());
        when(rawMatchTimelineRepository.findExistingMatchIds(Set.of("KR_1"))).thenReturn(Set.of());
        when(riotClient.getRawMatch("KR_1")).thenReturn("{\"match\":1}");
        when(riotClient.getRawMatchTimeline("KR_1")).thenReturn("{\"timeline\":1}");

        riotMatchSyncService.syncMatches(0, 1, 0, 1);

        verify(cohortPersistenceService).persist(argThat(saved ->
                saved.getMatchId().equals("KR_1")
                        && saved.getPuuid().equals("puuid-1")
                        && saved.getTier().equals("PLATINUM")));
    }

    @Test
    void 이번_리그_조회에서_전달한_PUUID만_최신_범위부터_수집한다() {
        when(riotClient.getMatchIds("puuid-current", 0, 20)).thenReturn(List.of("KR_1"));
        when(rawMatchRepository.findExistingMatchIds(Set.of("KR_1"))).thenReturn(Set.of());
        when(rawMatchTimelineRepository.findExistingMatchIds(Set.of("KR_1"))).thenReturn(Set.of());
        when(riotClient.getRawMatch("KR_1")).thenReturn("{\"match\":1}");
        when(riotClient.getRawMatchTimeline("KR_1")).thenReturn("{\"timeline\":1}");
        when(persistenceService.persist(any())).thenReturn(true);
        when(timelinePersistenceService.persist(any())).thenReturn(true);

        riotMatchSyncService.syncMatches(List.of("puuid-current"), 20);

        verify(playerRepository, never()).findPuuidsByPlatform(any(), any());
        verify(riotClient).getMatchIds("puuid-current", 0, 20);
    }

    @Test
    void 한_PUUID의_실패가_다른_PUUID의_수집을_막지_않는다() {
        when(riotClient.getMatchIds("puuid-failing", 0, 20))
                .thenThrow(new IllegalStateException("temporary failure"));
        when(riotClient.getMatchIds("puuid-succeeding", 0, 20)).thenReturn(List.of());

        RiotMatchSyncService.SyncResult result = riotMatchSyncService.syncMatches(
                List.of("puuid-failing", "puuid-succeeding"),
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
        when(rawMatchRepository.findExistingMatchIds(Set.of("KR_SHARED"))).thenReturn(Set.of());
        when(rawMatchTimelineRepository.findExistingMatchIds(Set.of("KR_SHARED"))).thenReturn(Set.of());
        when(riotClient.getRawMatch("KR_SHARED")).thenReturn("{\"match\":1}");
        when(riotClient.getRawMatchTimeline("KR_SHARED")).thenReturn("{\"timeline\":1}");
        when(persistenceService.persist(any())).thenReturn(true);
        when(timelinePersistenceService.persist(any())).thenReturn(true);

        riotMatchSyncService.syncMatches(List.of("puuid-1", "puuid-2"), 20);

        verify(riotClient, times(1)).getRawMatch("KR_SHARED");
        verify(riotClient, times(1)).getRawMatchTimeline("KR_SHARED");
        verify(persistenceService, times(1)).persist(any());
        verify(timelinePersistenceService, times(1)).persist(any());
    }

    @Test
    void 다시_실행하면_최신_범위를_재조회하고_저장된_매치_상세는_건너뛴다() {
        when(riotClient.getMatchIds("puuid-1", 0, 20)).thenReturn(List.of("KR_1"));
        when(rawMatchRepository.findExistingMatchIds(Set.of("KR_1")))
                .thenReturn(Set.of(), Set.of("KR_1"));
        when(rawMatchTimelineRepository.findExistingMatchIds(Set.of("KR_1")))
                .thenReturn(Set.of(), Set.of("KR_1"));
        when(riotClient.getRawMatch("KR_1")).thenReturn("{\"match\":1}");
        when(riotClient.getRawMatchTimeline("KR_1")).thenReturn("{\"timeline\":1}");
        when(persistenceService.persist(any())).thenReturn(true);
        when(timelinePersistenceService.persist(any())).thenReturn(true);

        riotMatchSyncService.syncMatches(List.of("puuid-1"), 20);
        riotMatchSyncService.syncMatches(List.of("puuid-1"), 20);

        verify(riotClient, times(2)).getMatchIds("puuid-1", 0, 20);
        verify(riotClient, times(1)).getRawMatch("KR_1");
        verify(riotClient, times(1)).getRawMatchTimeline("KR_1");
    }

    @Test
    void 조회_범위를_검증한다() {
        assertThatThrownBy(() -> riotMatchSyncService.syncMatches(-1, 20, 0, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("playerPage must not be negative");
        assertThatThrownBy(() -> riotMatchSyncService.syncMatches(0, 101, 0, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("playerCount must be between 1 and 100");
        assertThatThrownBy(() -> riotMatchSyncService.syncMatches(0, 20, -1, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("start must not be negative");
        assertThatThrownBy(() -> riotMatchSyncService.syncMatches(0, 20, 0, 101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("count must be between 1 and 100");

        verifyNoInteractions(
                riotClient,
                playerRepository,
                rawMatchRepository,
                rawMatchTimelineRepository,
                persistenceService,
                timelinePersistenceService
        );
    }
}
