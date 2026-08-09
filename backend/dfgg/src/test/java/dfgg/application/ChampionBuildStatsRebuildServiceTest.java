package dfgg.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dfgg.domain.item.Item;
import dfgg.domain.item.ItemRepository;
import dfgg.domain.match.MatchParticipantCohortRepository;
import dfgg.domain.match.RawMatch;
import dfgg.domain.match.RawMatchRepository;
import dfgg.domain.match.RawMatchTimeline;
import dfgg.domain.match.RawMatchTimelineRepository;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import dfgg.domain.stats.ChampionBuildStatsRepository;
import dfgg.domain.stats.CompositionStatsSampleRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class ChampionBuildStatsRebuildServiceTest {

    @Mock
    private RawMatchRepository rawMatchRepository;

    @Mock
    private RawMatchTimelineRepository rawMatchTimelineRepository;

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private NormalizedMatchParticipantRepository normalizedParticipantRepository;

    @Mock
    private CompositionStatsSampleRepository sampleRepository;

    @Mock
    private ChampionBuildStatsRepository statsRepository;

    @Mock
    private ChampionBuildStatsMatchProcessor matchProcessor;

    @Mock
    private MatchParticipantCohortRepository cohortRepository;

    @InjectMocks
    private ChampionBuildStatsRebuildService rebuildService;

    @Test
    void 전체_통계_집계는_기존_파생_데이터를_삭제하지_않는다() {
        ChampionBuildStatsRebuildResult result = rebuildService.rebuildAll("PLATINUM");

        assertThat(result).isEqualTo(new ChampionBuildStatsRebuildResult(0, 0, 0, 0));
        verify(sampleRepository, never()).deleteAllInBatch();
        verify(statsRepository, never()).deleteAll();
        verify(normalizedParticipantRepository, never()).deleteAllInBatch();
    }

    @Test
    void 원본_매치와_Timeline을_정규화하고_통계를_집계한다() {
        RawMatch rawMatch = new RawMatch("KR_1", "{\"info\":{}}");
        RawMatchTimeline timeline = new RawMatchTimeline("KR_1", "{\"info\":{}}");
        when(itemRepository.findAll()).thenReturn(List.of(new Item(3071L, "아이템 A")));
        when(rawMatchRepository.findById("KR_1")).thenReturn(Optional.of(rawMatch));
        when(rawMatchTimelineRepository.findById("KR_1")).thenReturn(Optional.of(timeline));
        when(matchProcessor.rebuild(any(), any(), anyString(), anyCollection(), anySet())).thenReturn(32);

        int recorded = rebuildService.rebuildOne("KR_1", "PLATINUM", List.of("p-1"));

        assertThat(recorded).isEqualTo(32);
        verify(matchProcessor).rebuild(rawMatch, timeline, "PLATINUM", List.of("p-1"), Set.of(3071));
    }

    @Test
    void 전체_집계에서는_Timeline이_없는_매치를_결과와_로그에_기록한다(CapturedOutput output) {
        RawMatch rawMatch = new RawMatch("KR_1", "{\"info\":{}}");
        when(rawMatchRepository.findAll()).thenReturn(List.of(rawMatch));
        when(itemRepository.findAll()).thenReturn(List.of());
        when(rawMatchTimelineRepository.findById("KR_1")).thenReturn(Optional.empty());

        ChampionBuildStatsRebuildResult result = rebuildService.rebuildAll("PLATINUM", List.of());

        assertThat(result).isEqualTo(new ChampionBuildStatsRebuildResult(1, 0, 1, 0));
        assertThat(output)
                .contains("Champion build stats rebuild started: tier=PLATINUM, totalMatches=1")
                .contains("Champion build stats rebuild progress: tier=PLATINUM, visitedMatches=1/1")
                .contains("Champion build stats rebuild completed: tier=PLATINUM, totalMatches=1")
                .contains("skippedMissingTimeline=1");
        verifyNoInteractions(matchProcessor);
    }

    @Test
    void 한_매치가_실패해도_다음_매치를_계속_처리한다(CapturedOutput output) {
        RawMatch failedMatch = new RawMatch("KR_FAILED", "{\"info\":{}}");
        RawMatch succeededMatch = new RawMatch("KR_SUCCEEDED", "{\"info\":{}}");
        RawMatchTimeline failedTimeline = new RawMatchTimeline("KR_FAILED", "{\"info\":{}}");
        RawMatchTimeline succeededTimeline = new RawMatchTimeline("KR_SUCCEEDED", "{\"info\":{}}");
        when(itemRepository.findAll()).thenReturn(List.of());
        when(rawMatchRepository.findAll()).thenReturn(List.of(failedMatch, succeededMatch));
        when(rawMatchTimelineRepository.findById("KR_FAILED")).thenReturn(Optional.of(failedTimeline));
        when(rawMatchTimelineRepository.findById("KR_SUCCEEDED")).thenReturn(Optional.of(succeededTimeline));
        when(matchProcessor.rebuild(failedMatch, failedTimeline, "PLATINUM", List.of(), Set.of()))
                .thenThrow(new IllegalStateException("invalid match data"));
        when(matchProcessor.rebuild(succeededMatch, succeededTimeline, "PLATINUM", List.of(), Set.of()))
                .thenReturn(32);

        ChampionBuildStatsRebuildResult result = rebuildService.rebuildAll("PLATINUM", List.of());

        assertThat(result).isEqualTo(new ChampionBuildStatsRebuildResult(
                2,
                1,
                0,
                1,
                32,
                List.of(new ChampionBuildStatsRebuildResult.Failure(
                        "KR_FAILED",
                        "IllegalStateException: invalid match data"
                ))
        ));
        assertThat(output)
                .contains("Champion build stats match failed and will be skipped")
                .contains("matchId=KR_FAILED")
                .contains("failedMatches=1");
        verify(matchProcessor).rebuild(succeededMatch, succeededTimeline, "PLATINUM", List.of(), Set.of());
    }
}
