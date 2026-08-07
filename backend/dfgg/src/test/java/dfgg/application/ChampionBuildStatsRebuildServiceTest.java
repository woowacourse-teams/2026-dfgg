package dfgg.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dfgg.domain.item.Item;
import dfgg.domain.item.ItemRepository;
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

@ExtendWith(MockitoExtension.class)
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

    @InjectMocks
    private ChampionBuildStatsRebuildService rebuildService;

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
    void 전체_재생성에서는_Timeline이_없는_매치를_건너뛴다() {
        RawMatch rawMatch = new RawMatch("KR_1", "{\"info\":{}}");
        when(rawMatchRepository.findAll()).thenReturn(List.of(rawMatch));
        when(itemRepository.findAll()).thenReturn(List.of());
        when(rawMatchTimelineRepository.findById("KR_1")).thenReturn(Optional.empty());

        int recorded = rebuildService.rebuildAll("PLATINUM", List.of());

        assertThat(recorded).isZero();
        verifyNoInteractions(matchNormalizer, normalizedPersistenceService, aggregationService);
    }
}
