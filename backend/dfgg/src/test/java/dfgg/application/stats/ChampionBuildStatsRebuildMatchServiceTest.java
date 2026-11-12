package dfgg.application.stats;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dfgg.application.item.ItemService;
import dfgg.application.match.MatchNormalizationService;
import dfgg.domain.match.NormalizedMatch;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import dfgg.domain.match.NormalizedMatchParticipant;
import dfgg.domain.match.RawMatch;
import dfgg.domain.match.RawMatchRepository;
import dfgg.domain.match.RawMatchTimeline;
import dfgg.domain.match.RawMatchTimelineRepository;
import dfgg.domain.stats.CompositionStatsSampleRepository;
import dfgg.domain.stats.StatsAggregationCompletionRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChampionBuildStatsRebuildMatchServiceTest {

    @Mock
    private RawMatchRepository rawMatchRepository;

    @Mock
    private RawMatchTimelineRepository rawMatchTimelineRepository;

    @Mock
    private ItemService itemService;

    @Mock
    private MatchNormalizationService matchNormalizationService;

    @Mock
    private ChampionBuildStatsAggregationService aggregationService;

    @Mock
    private StatsAggregationCompletionRepository completionRepository;

    @Mock
    private CompositionStatsSampleRepository sampleRepository;

    @Mock
    private NormalizedMatchParticipantRepository participantRepository;

    private ChampionBuildStatsRebuildMatchService rebuildService;

    @BeforeEach
    void setUp() {
        rebuildService = new ChampionBuildStatsRebuildMatchService(
                rawMatchRepository,
                rawMatchTimelineRepository,
                itemService,
                matchNormalizationService,
                aggregationService,
                completionRepository,
                sampleRepository,
                participantRepository,
                100
        );
    }

    @Test
    void 처리할_신규_대상이_없으면_정규화_데이터를_조회하지_않는다() {
        rebuildService.rebuildAll("PLATINUM");

        verify(completionRepository).findPendingTargetsAfter(
                "RANKED_SOLO_5x5", "PLATINUM", "v1", "", "", 100
        );
        verifyNoInteractions(
                rawMatchRepository,
                rawMatchTimelineRepository,
                itemService,
                participantRepository,
                matchNormalizationService,
                aggregationService,
                sampleRepository
        );
    }

    @Test
    void 지정한_매치의_기존_통계_기여분을_다시_집계한다() {
        RawMatch rawMatch = new RawMatch("KR_1", "{\"info\":{}}");
        RawMatchTimeline timeline = new RawMatchTimeline("KR_1", "{\"info\":{}}");
        when(participantRepository.findPuuidsByMatchIdAndTier(
                "KR_1", "PLATINUM"
        )).thenReturn(List.of("p-1"));
        when(itemService.findCoreItemIds()).thenReturn(Set.of(3071));
        when(rawMatchRepository.findById("KR_1")).thenReturn(Optional.of(rawMatch));
        when(rawMatchTimelineRepository.findById("KR_1")).thenReturn(Optional.of(timeline));
        NormalizedMatch normalized = normalizedMatch(
                "KR_1",
                normalizedParticipant("p-1", 1)
        );
        when(matchNormalizationService.normalizeAsTierSample(
                "KR_1",
                rawMatch.getRawData(),
                timeline.getRawData(),
                Set.of(3071),
                "PLATINUM"
        )).thenReturn(normalized);
        rebuildService.replayOne("KR_1", "PLATINUM");

        verify(matchNormalizationService).normalizeAsTierSample(
                "KR_1", rawMatch.getRawData(), timeline.getRawData(), Set.of(3071), "PLATINUM"
        );
        verify(aggregationService).aggregate(normalized, "PLATINUM", List.of("p-1"));
    }

    @Test
    void 전체_집계에서는_저장된_정규화_행을_읽어_통계를_집계한다() {
        NormalizedMatchParticipant firstParticipant = normalizedParticipant("p-1", 1);
        NormalizedMatchParticipant secondParticipant = normalizedParticipant("p-2", 2);
        NormalizedMatch normalized = normalizedMatch(
                "KR_1",
                firstParticipant,
                secondParticipant
        );
        when(completionRepository.findPendingTargetsAfter(
                "RANKED_SOLO_5x5", "PLATINUM", "v1", "", "", 100
        )).thenReturn(List.of(target("KR_1", "p-1")));
        when(participantRepository.findByMatchId("KR_1")).thenReturn(List.of(
                normalizedRow(normalized, secondParticipant),
                normalizedRow(normalized, firstParticipant)
        ));
        when(completionRepository.insertIfAbsent(
                "KR_1", "p-1", "RANKED_SOLO_5x5", "PLATINUM", "v1"
        )).thenReturn(1);
        rebuildService.rebuildAll("PLATINUM");

        verify(aggregationService).aggregate(normalized, "PLATINUM", List.of("p-1"));
        verifyNoInteractions(rawMatchRepository, rawMatchTimelineRepository, itemService);
    }

    @Test
    void 한_매치가_실패해도_다음_매치를_계속_처리한다() {
        NormalizedMatchParticipant failedParticipant = normalizedParticipant("p-failed", 1);
        NormalizedMatchParticipant succeededParticipant = normalizedParticipant("p-succeeded", 1);
        NormalizedMatch failedMatch = normalizedMatch("KR_FAILED", failedParticipant);
        NormalizedMatch succeededMatch = normalizedMatch("KR_SUCCEEDED", succeededParticipant);
        when(completionRepository.findPendingTargetsAfter(
                "RANKED_SOLO_5x5", "PLATINUM", "v1", "", "", 100
        )).thenReturn(List.of(
                target("KR_FAILED", "p-failed"),
                target("KR_SUCCEEDED", "p-succeeded")
        ));
        when(participantRepository.findByMatchId("KR_FAILED")).thenReturn(List.of(
                normalizedRow(failedMatch, failedParticipant)
        ));
        when(participantRepository.findByMatchId("KR_SUCCEEDED")).thenReturn(List.of(
                normalizedRow(succeededMatch, succeededParticipant)
        ));
        when(completionRepository.insertIfAbsent(
                "KR_FAILED", "p-failed", "RANKED_SOLO_5x5", "PLATINUM", "v1"
        )).thenReturn(1);
        when(completionRepository.insertIfAbsent(
                "KR_SUCCEEDED", "p-succeeded", "RANKED_SOLO_5x5", "PLATINUM", "v1"
        )).thenReturn(1);
        doThrow(new IllegalStateException("invalid match data"))
                .when(aggregationService)
                .aggregate(
                        failedMatch,
                        "PLATINUM",
                        List.of("p-failed")
                );
        assertThatThrownBy(() -> rebuildService.rebuildAll("PLATINUM"))
                .isInstanceOf(IllegalStateException.class);
        verify(aggregationService).aggregate(succeededMatch, "PLATINUM", List.of("p-succeeded"));
        verifyNoInteractions(rawMatchRepository, rawMatchTimelineRepository, itemService);
    }

    private NormalizedMatch normalizedMatch(
            String matchId,
            NormalizedMatchParticipant... participants
    ) {
        return new NormalizedMatch(matchId, "16.15", 420, List.of(participants));
    }

    private NormalizedMatchParticipant normalizedParticipant(String puuid, int participantId) {
        return new NormalizedMatchParticipant(
                puuid,
                participantId,
                participantId,
                100,
                "TOP",
                "PLATINUM",
                true,
                List.of(3071),
                List.of(3071),
                true
        );
    }

    private NormalizedMatchParticipant normalizedRow(
            NormalizedMatch match,
            NormalizedMatchParticipant participant
    ) {
        return participant;
    }

    private StatsAggregationCompletionRepository.PendingTarget target(String matchId, String puuid) {
        return new StatsAggregationCompletionRepository.PendingTarget() {
            @Override
            public String getMatchId() {
                return matchId;
            }

            @Override
            public String getPuuid() {
                return puuid;
            }
        };
    }
}
