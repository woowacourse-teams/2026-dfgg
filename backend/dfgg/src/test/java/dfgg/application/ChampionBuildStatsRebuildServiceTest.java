package dfgg.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dfgg.application.item.ItemService;
import dfgg.domain.match.MatchParticipantCohortRepository;
import dfgg.domain.match.RawMatch;
import dfgg.domain.match.RawMatchRepository;
import dfgg.domain.match.RawMatchTimeline;
import dfgg.domain.match.RawMatchTimelineRepository;
import dfgg.domain.stats.StatsAggregationCompletionRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    private ItemService itemService;

    @Mock
    private ChampionBuildStatsMatchProcessor matchProcessor;

    @Mock
    private StatsAggregationCompletionRepository completionRepository;

    @Mock
    private MatchParticipantCohortRepository cohortRepository;

    private ChampionBuildStatsRebuildService rebuildService;

    @BeforeEach
    void setUp() {
        rebuildService = new ChampionBuildStatsRebuildService(
                rawMatchRepository,
                rawMatchTimelineRepository,
                itemService,
                matchProcessor,
                completionRepository,
                cohortRepository,
                100
        );
    }

    @Test
    void 처리할_신규_대상이_없으면_원본_데이터를_조회하지_않는다() {
        ChampionBuildStatsRebuildResult result = rebuildService.rebuildAll("PLATINUM");

        assertThat(result).isEqualTo(new ChampionBuildStatsRebuildResult(0, 0, 0, 0));
        verify(completionRepository).countPendingMatches("RANKED_SOLO_5x5", "PLATINUM", "v1");
        verifyNoInteractions(rawMatchRepository, rawMatchTimelineRepository, itemService, matchProcessor);
    }

    @Test
    void 지정한_매치의_기존_통계_기여분을_다시_집계한다() {
        RawMatch rawMatch = new RawMatch("KR_1", "{\"info\":{}}");
        RawMatchTimeline timeline = new RawMatchTimeline("KR_1", "{\"info\":{}}");
        when(cohortRepository.findPuuidsByMatchIdAndQueueTypeAndTier(
                "KR_1", "RANKED_SOLO_5x5", "PLATINUM"
        )).thenReturn(List.of("p-1"));
        when(itemService.findCoreItemIds()).thenReturn(Set.of(3071));
        when(rawMatchRepository.findById("KR_1")).thenReturn(Optional.of(rawMatch));
        when(rawMatchTimelineRepository.findById("KR_1")).thenReturn(Optional.of(timeline));
        when(matchProcessor.replay(
                any(),
                any(),
                anyString(),
                anyString(),
                anyCollection(),
                anySet(),
                anyString()
        )).thenReturn(new ChampionBuildStatsMatchProcessor.ReplayResult(1, 32));

        ChampionBuildStatsRebuildResult result = rebuildService.replayOne("KR_1", "PLATINUM");

        assertThat(result).isEqualTo(new ChampionBuildStatsRebuildResult(1, 1, 0, 32));
        verify(matchProcessor).replay(
                rawMatch,
                timeline,
                "RANKED_SOLO_5x5",
                "PLATINUM",
                List.of("p-1"),
                Set.of(3071),
                "v1"
        );
    }

    @Test
    void 전체_집계에서는_Timeline이_없는_매치를_결과와_로그에_기록한다(CapturedOutput output) {
        RawMatch rawMatch = new RawMatch("KR_1", "{\"info\":{}}");
        when(completionRepository.countPendingMatches("RANKED_SOLO_5x5", "PLATINUM", "v1"))
                .thenReturn(1L);
        when(completionRepository.findPendingTargetsAfter(
                "RANKED_SOLO_5x5", "PLATINUM", "v1", "", "", 100
        )).thenReturn(List.of(target("KR_1", "p-1")));
        when(itemService.findCoreItemIds()).thenReturn(Set.of());
        when(rawMatchRepository.findById("KR_1")).thenReturn(Optional.of(rawMatch));
        when(rawMatchTimelineRepository.findById("KR_1")).thenReturn(Optional.empty());

        ChampionBuildStatsRebuildResult result = rebuildService.rebuildAll("PLATINUM");

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
        when(completionRepository.countPendingMatches("RANKED_SOLO_5x5", "PLATINUM", "v1"))
                .thenReturn(2L);
        when(completionRepository.findPendingTargetsAfter(
                "RANKED_SOLO_5x5", "PLATINUM", "v1", "", "", 100
        )).thenReturn(List.of(
                target("KR_FAILED", "p-failed"),
                target("KR_SUCCEEDED", "p-succeeded")
        ));
        when(itemService.findCoreItemIds()).thenReturn(Set.of());
        when(rawMatchRepository.findById("KR_FAILED")).thenReturn(Optional.of(failedMatch));
        when(rawMatchRepository.findById("KR_SUCCEEDED")).thenReturn(Optional.of(succeededMatch));
        when(rawMatchTimelineRepository.findById("KR_FAILED")).thenReturn(Optional.of(failedTimeline));
        when(rawMatchTimelineRepository.findById("KR_SUCCEEDED")).thenReturn(Optional.of(succeededTimeline));
        when(matchProcessor.rebuild(
                failedMatch,
                failedTimeline,
                "RANKED_SOLO_5x5",
                "PLATINUM",
                List.of("p-failed"),
                Set.of(),
                "v1"
        ))
                .thenThrow(new IllegalStateException("invalid match data"));
        when(matchProcessor.rebuild(
                succeededMatch,
                succeededTimeline,
                "RANKED_SOLO_5x5",
                "PLATINUM",
                List.of("p-succeeded"),
                Set.of(),
                "v1"
        )).thenReturn(new ChampionBuildStatsMatchProcessor.Result(1, 32));

        ChampionBuildStatsRebuildResult result = rebuildService.rebuildAll("PLATINUM");

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
        verify(matchProcessor).rebuild(
                succeededMatch,
                succeededTimeline,
                "RANKED_SOLO_5x5",
                "PLATINUM",
                List.of("p-succeeded"),
                Set.of(),
                "v1"
        );
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
