package dfgg.application.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dfgg.domain.match.RawMatchRepository;
import dfgg.domain.match.RawMatchTimelineRepository;
import dfgg.infrastructure.external.client.RiotClient;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class RawMatchTimelineServiceTest {

    @Mock
    private RiotClient riotClient;

    @Mock
    private RawMatchRepository rawMatchRepository;

    @Mock
    private RawMatchTimelineRepository rawMatchTimelineRepository;

    @InjectMocks
    private RawMatchTimelineService rawMatchTimelineService;

    @Test
    void 저장된_Timeline의_매치_ID를_조회한다() {
        when(rawMatchTimelineRepository.findExistingMatchIds(Set.of("KR_1", "KR_2")))
                .thenReturn(Set.of("KR_1"));

        Set<String> matchIds = rawMatchTimelineService.findExistingMatchIds(
                Set.of("KR_1", "KR_2")
        );

        assertThat(matchIds).containsExactly("KR_1");
    }

    @Test
    void Riot_API에서_Timeline_원본을_조회하고_저장한다() {
        when(riotClient.getRawMatchTimeline("KR_1")).thenReturn("{\"timeline\":1}");
        when(rawMatchTimelineRepository.insertIfAbsent("KR_1", "{\"timeline\":1}"))
                .thenReturn(1);

        boolean collected = rawMatchTimelineService.collectRawMatchTimeline("KR_1");

        assertThat(collected).isTrue();
        verify(riotClient).getRawMatchTimeline("KR_1");
        verify(rawMatchTimelineRepository).insertIfAbsent("KR_1", "{\"timeline\":1}");
    }

    @Test
    void 누락된_Timeline을_수집하고_개별_실패가_발생해도_계속_진행한다() {
        when(rawMatchRepository.findMatchIdsMissingTimelineAfter(
                "", PageRequest.of(0, 100)
        )).thenReturn(List.of("KR_1", "KR_2"));
        when(rawMatchRepository.findMatchIdsMissingTimelineAfter(
                "KR_2", PageRequest.of(0, 100)
        )).thenReturn(List.of());
        when(riotClient.getRawMatchTimeline("KR_1"))
                .thenThrow(new IllegalStateException("temporary failure"));
        when(riotClient.getRawMatchTimeline("KR_2")).thenReturn("{\"timeline\":2}");
        when(rawMatchTimelineRepository.insertIfAbsent("KR_2", "{\"timeline\":2}"))
                .thenReturn(1);

        RawMatchTimelineService.MissingTimelineSyncResult result =
                rawMatchTimelineService.collectMissingTimelines();

        assertThat(result.newTimelines()).isEqualTo(1);
        assertThat(result.skippedItems()).isZero();
        assertThat(result.failures())
                .extracting(
                        RawMatchTimelineService.Failure::matchId,
                        RawMatchTimelineService.Failure::reason
                )
                .containsExactly(tuple("KR_1", "IllegalStateException: temporary failure"));
        verify(riotClient).getRawMatchTimeline("KR_2");
    }

    @Test
    void Timeline_저장_경합은_건너뛴_항목으로_집계한다() {
        when(rawMatchRepository.findMatchIdsMissingTimelineAfter(
                "", PageRequest.of(0, 100)
        )).thenReturn(List.of("KR_1"));
        when(rawMatchRepository.findMatchIdsMissingTimelineAfter(
                "KR_1", PageRequest.of(0, 100)
        )).thenReturn(List.of());
        when(riotClient.getRawMatchTimeline("KR_1")).thenReturn("{\"timeline\":1}");
        when(rawMatchTimelineRepository.insertIfAbsent("KR_1", "{\"timeline\":1}"))
                .thenReturn(0);

        RawMatchTimelineService.MissingTimelineSyncResult result =
                rawMatchTimelineService.collectMissingTimelines();

        assertThat(result.newTimelines()).isZero();
        assertThat(result.skippedItems()).isEqualTo(1);
        assertThat(result.failures()).isEmpty();
    }
}
