package dfgg.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dfgg.domain.item.Item;
import dfgg.domain.item.ItemRepository;
import dfgg.domain.match.MatchParticipantCohortRepository;
import dfgg.domain.match.NormalizedMatch;
import dfgg.domain.match.RawMatch;
import dfgg.domain.match.RawMatchRepository;
import dfgg.domain.match.RawMatchTimeline;
import dfgg.domain.match.RawMatchTimelineRepository;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import dfgg.domain.stats.ChampionBuildStatsRepository;
import dfgg.domain.stats.CompositionStatsSampleRepository;
import java.util.List;
import java.util.Optional;
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
    private MatchNormalizer matchNormalizer;

    @Mock
    private NormalizedMatchPersistenceService normalizedPersistenceService;

    @Mock
    private ChampionBuildStatsAggregationService aggregationService;

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
        NormalizedMatch normalized = new NormalizedMatch(
                "KR_1", "16.15", 420, List.of()
        );
        when(itemRepository.findAll()).thenReturn(List.of(new Item(3071L, "아이템 A")));
        when(rawMatchRepository.findById("KR_1")).thenReturn(Optional.of(rawMatch));
        when(rawMatchTimelineRepository.findById("KR_1")).thenReturn(Optional.of(timeline));
        when(matchNormalizer.normalize(anyString(), anyString(), anyString(), anyCollection()))
                .thenReturn(normalized);
        when(aggregationService.aggregate(any(), anyString(), anyCollection())).thenReturn(32);

        int recorded = rebuildService.rebuildOne("KR_1", "PLATINUM", List.of("p-1"));

        assertThat(recorded).isEqualTo(32);
        verify(normalizedPersistenceService).replace(normalized);
        verify(aggregationService).aggregate(normalized, "PLATINUM", List.of("p-1"));
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
        verifyNoInteractions(matchNormalizer, normalizedPersistenceService, aggregationService);
    }
}
