package dfgg.application.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dfgg.infrastructure.external.client.RiotClient;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
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

    @InjectMocks
    private RiotMatchSyncService riotMatchSyncService;

    @Test
    void 한_플레이어의_매치_ID를_조회한다() {
        when(riotClient.getMatchIds("puuid-1", 0, 1))
                .thenReturn(List.of("KR_1"));

        assertThat(riotMatchSyncService.findMatchIds("puuid-1", 0, 1))
                .containsExactly("KR_1");
        verify(riotClient).getMatchIds("puuid-1", 0, 1);
        verifyNoInteractions(rawMatchService, rawMatchTimelineService);
    }

    @Test
    void 단일_매치는_Match와_Timeline을_순서대로_수집한다() {
        when(rawMatchService.findExistingMatchIds(Set.of("KR_1"))).thenReturn(Set.of());
        when(rawMatchTimelineService.findExistingMatchIds(Set.of("KR_1"))).thenReturn(Set.of());
        when(rawMatchService.collectRawMatch("KR_1")).thenReturn(true);
        when(rawMatchTimelineService.collectRawMatchTimeline("KR_1")).thenReturn(true);

        boolean collected = riotMatchSyncService.syncMatch("KR_1");

        assertThat(collected).isTrue();
        InOrder order = inOrder(rawMatchService, rawMatchTimelineService);
        order.verify(rawMatchService).collectRawMatch("KR_1");
        order.verify(rawMatchTimelineService).collectRawMatchTimeline("KR_1");
    }

    @Test
    void 플레이어와_매치_ID를_중복_제거하고_매치별_원본을_한_번만_수집한다() {
        when(riotClient.getMatchIds("puuid-1", 0, 20))
                .thenReturn(List.of("KR_1", "KR_SHARED"));
        when(riotClient.getMatchIds("puuid-2", 0, 20))
                .thenReturn(List.of("KR_SHARED", "KR_2"));
        when(rawMatchService.findExistingMatchIds(Set.of("KR_1", "KR_SHARED", "KR_2")))
                .thenReturn(Set.of());
        when(rawMatchTimelineService.findExistingMatchIds(Set.of("KR_1", "KR_SHARED", "KR_2")))
                .thenReturn(Set.of());
        when(rawMatchService.collectRawMatch("KR_1")).thenReturn(true);
        when(rawMatchService.collectRawMatch("KR_SHARED")).thenReturn(true);
        when(rawMatchService.collectRawMatch("KR_2")).thenReturn(true);
        when(rawMatchTimelineService.collectRawMatchTimeline("KR_1")).thenReturn(true);
        when(rawMatchTimelineService.collectRawMatchTimeline("KR_SHARED")).thenReturn(true);
        when(rawMatchTimelineService.collectRawMatchTimeline("KR_2")).thenReturn(true);

        riotMatchSyncService.syncMatches(
                List.of("puuid-1", "puuid-1", "puuid-2"),
                0,
                20
        );

        verify(riotClient, times(1)).getMatchIds("puuid-1", 0, 20);
        verify(riotClient, times(1)).getMatchIds("puuid-2", 0, 20);
        verify(rawMatchService, times(1)).collectRawMatch("KR_SHARED");
        verify(rawMatchTimelineService, times(1)).collectRawMatchTimeline("KR_SHARED");
    }

    @Test
    void 수집할_플레이어가_없으면_아무것도_호출하지_않는다() {
        riotMatchSyncService.syncMatches(List.of(), 0, 20);

        verifyNoInteractions(riotClient, rawMatchService, rawMatchTimelineService);
    }

    @Test
    void 이미_저장된_Match와_Timeline은_재수집하지_않는다() {
        when(riotClient.getMatchIds("puuid-1", 0, 20)).thenReturn(List.of("KR_1"));
        when(rawMatchService.findExistingMatchIds(Set.of("KR_1"))).thenReturn(Set.of("KR_1"));
        when(rawMatchTimelineService.findExistingMatchIds(Set.of("KR_1"))).thenReturn(Set.of("KR_1"));

        riotMatchSyncService.syncMatches(
                List.of("puuid-1"),
                0,
                20
        );

        verify(rawMatchService, never()).collectRawMatch("KR_1");
        verify(rawMatchTimelineService, never()).collectRawMatchTimeline("KR_1");
    }

    @Test
    void Match만_저장된_경우_Timeline만_보완한다() {
        when(riotClient.getMatchIds("puuid-1", 0, 20)).thenReturn(List.of("KR_1"));
        when(rawMatchService.findExistingMatchIds(Set.of("KR_1"))).thenReturn(Set.of("KR_1"));
        when(rawMatchTimelineService.findExistingMatchIds(Set.of("KR_1"))).thenReturn(Set.of());
        when(rawMatchTimelineService.collectRawMatchTimeline("KR_1")).thenReturn(true);

        riotMatchSyncService.syncMatches(
                List.of("puuid-1"),
                0,
                20
        );

        verify(rawMatchService, never()).collectRawMatch("KR_1");
        verify(rawMatchTimelineService).collectRawMatchTimeline("KR_1");
    }

    @Test
    void 새로운_매치는_Match를_먼저_저장하고_Timeline을_저장한다() {
        when(riotClient.getMatchIds("puuid-1", 0, 20)).thenReturn(List.of("KR_1"));
        when(rawMatchService.findExistingMatchIds(Set.of("KR_1"))).thenReturn(Set.of());
        when(rawMatchTimelineService.findExistingMatchIds(Set.of("KR_1"))).thenReturn(Set.of());
        when(rawMatchService.collectRawMatch("KR_1")).thenReturn(true);
        when(rawMatchTimelineService.collectRawMatchTimeline("KR_1")).thenReturn(true);

        riotMatchSyncService.syncMatches(
                List.of("puuid-1"),
                0,
                20
        );

        InOrder order = inOrder(rawMatchService, rawMatchTimelineService);
        order.verify(rawMatchService).collectRawMatch("KR_1");
        order.verify(rawMatchTimelineService).collectRawMatchTimeline("KR_1");
    }

    @Test
    void Match_수집이_실패하면_해당_Timeline은_건너뛰고_다음_매치를_계속_수집한다() {
        when(riotClient.getMatchIds("puuid-1", 0, 2)).thenReturn(List.of("KR_1", "KR_2"));
        when(rawMatchService.findExistingMatchIds(Set.of("KR_1", "KR_2"))).thenReturn(Set.of());
        when(rawMatchTimelineService.findExistingMatchIds(Set.of("KR_1", "KR_2"))).thenReturn(Set.of());
        when(rawMatchService.collectRawMatch("KR_1"))
                .thenThrow(new IllegalStateException("match failed"));
        when(rawMatchService.collectRawMatch("KR_2")).thenReturn(true);
        when(rawMatchTimelineService.collectRawMatchTimeline("KR_2")).thenReturn(true);

        assertThatThrownBy(() -> riotMatchSyncService.syncMatches(
                List.of("puuid-1"),
                0,
                2
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("match failed");

        verify(rawMatchTimelineService, never()).collectRawMatchTimeline("KR_1");
        verify(rawMatchService).collectRawMatch("KR_2");
        verify(rawMatchTimelineService).collectRawMatchTimeline("KR_2");
    }

    @Test
    void Timeline_수집이_실패하면_예외를_전달한다() {
        when(riotClient.getMatchIds("puuid-1", 0, 1)).thenReturn(List.of("KR_1"));
        when(rawMatchService.findExistingMatchIds(Set.of("KR_1"))).thenReturn(Set.of());
        when(rawMatchTimelineService.findExistingMatchIds(Set.of("KR_1"))).thenReturn(Set.of());
        when(rawMatchService.collectRawMatch("KR_1")).thenReturn(true);
        when(rawMatchTimelineService.collectRawMatchTimeline("KR_1"))
                .thenThrow(new IllegalStateException("temporary failure"));

        assertThatThrownBy(() -> riotMatchSyncService.syncMatches(
                List.of("puuid-1"),
                0,
                1
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("temporary failure");
    }

    @Test
    void 실패한_Timeline은_다음_실행에서_다시_수집한다() {
        when(riotClient.getMatchIds("puuid-1", 0, 1)).thenReturn(List.of("KR_1"));
        when(rawMatchService.findExistingMatchIds(Set.of("KR_1")))
                .thenReturn(Set.of())
                .thenReturn(Set.of("KR_1"));
        when(rawMatchTimelineService.findExistingMatchIds(Set.of("KR_1")))
                .thenReturn(Set.of());
        when(rawMatchService.collectRawMatch("KR_1")).thenReturn(true);
        when(rawMatchTimelineService.collectRawMatchTimeline("KR_1"))
                .thenThrow(new IllegalStateException("temporary failure"))
                .thenReturn(true);

        assertThatThrownBy(() -> riotMatchSyncService.syncMatches(
                List.of("puuid-1"),
                0,
                1
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("temporary failure");
        riotMatchSyncService.syncMatches(
                List.of("puuid-1"),
                0,
                1
        );

        verify(rawMatchService, times(1)).collectRawMatch("KR_1");
        verify(rawMatchTimelineService, times(2)).collectRawMatchTimeline("KR_1");
    }

    @Test
    void 누락_Timeline_수집은_RawMatchTimelineService에_위임한다() {
        riotMatchSyncService.syncMissingTimelines();

        verify(rawMatchTimelineService).collectMissingTimelines();
        verifyNoInteractions(riotClient, rawMatchService);
    }

    @Test
    void 한_PUUID의_조회_실패가_다른_PUUID의_수집을_막지_않는다() {
        when(riotClient.getMatchIds("puuid-failing", 10, 20))
                .thenThrow(new IllegalStateException("temporary failure"));
        when(riotClient.getMatchIds("puuid-succeeding", 10, 20)).thenReturn(List.of("KR_1"));
        when(rawMatchService.findExistingMatchIds(Set.of("KR_1"))).thenReturn(Set.of());
        when(rawMatchTimelineService.findExistingMatchIds(Set.of("KR_1"))).thenReturn(Set.of());
        when(rawMatchService.collectRawMatch("KR_1")).thenReturn(true);
        when(rawMatchTimelineService.collectRawMatchTimeline("KR_1")).thenReturn(true);

        assertThatThrownBy(() -> riotMatchSyncService.syncMatches(
                List.of("puuid-failing", "puuid-succeeding"),
                10,
                20
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("temporary failure");

        verify(riotClient).getMatchIds("puuid-succeeding", 10, 20);
        verify(rawMatchService).collectRawMatch("KR_1");
        verify(rawMatchTimelineService).collectRawMatchTimeline("KR_1");
    }

    @Test
    void 다시_실행하면_같은_최신_범위를_조회하고_저장된_원본은_건너뛴다() {
        when(riotClient.getMatchIds("puuid-1", 0, 20)).thenReturn(List.of("KR_1"));
        when(rawMatchService.findExistingMatchIds(Set.of("KR_1")))
                .thenReturn(Set.of())
                .thenReturn(Set.of("KR_1"));
        when(rawMatchTimelineService.findExistingMatchIds(Set.of("KR_1")))
                .thenReturn(Set.of())
                .thenReturn(Set.of("KR_1"));
        when(rawMatchService.collectRawMatch("KR_1")).thenReturn(true);
        when(rawMatchTimelineService.collectRawMatchTimeline("KR_1")).thenReturn(true);

        riotMatchSyncService.syncMatches(List.of("puuid-1"), 0, 20);
        riotMatchSyncService.syncMatches(List.of("puuid-1"), 0, 20);

        verify(riotClient, times(2)).getMatchIds("puuid-1", 0, 20);
        verify(rawMatchService, times(1)).collectRawMatch("KR_1");
        verify(rawMatchTimelineService, times(1)).collectRawMatchTimeline("KR_1");
    }
}
