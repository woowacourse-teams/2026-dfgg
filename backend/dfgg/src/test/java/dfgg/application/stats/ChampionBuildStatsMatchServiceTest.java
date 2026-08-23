package dfgg.application.stats;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dfgg.domain.match.NormalizedMatch;
import dfgg.domain.match.NormalizedParticipant;
import dfgg.domain.stats.StatsAggregationCompletionRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChampionBuildStatsMatchServiceTest {

    @Mock
    private ChampionBuildStatsAggregationService aggregationService;

    @Mock
    private StatsAggregationCompletionRepository completionRepository;

    @InjectMocks
    private ChampionBuildStatsMatchService matchService;

    @Test
    void 지정한_티어의_참가자만_선점해_통계_집계에_전달한다() {
        NormalizedMatch normalized = normalizedMatch(
                participant("p-platinum-2", 1, "PLATINUM"),
                participant("p-gold", 2, "GOLD"),
                participant("p-platinum-1", 3, "PLATINUM")
        );
        when(completionRepository.insertIfAbsent(
                "KR_1", "p-platinum-1", "RANKED_SOLO_5x5", "PLATINUM", "v1"
        )).thenReturn(1);
        when(completionRepository.insertIfAbsent(
                "KR_1", "p-platinum-2", "RANKED_SOLO_5x5", "PLATINUM", "v1"
        )).thenReturn(1);
        matchService.registerMatchStats(normalized, "PLATINUM");

        verify(completionRepository).insertIfAbsent(
                "KR_1", "p-platinum-1", "RANKED_SOLO_5x5", "PLATINUM", "v1"
        );
        verify(completionRepository).insertIfAbsent(
                "KR_1", "p-platinum-2", "RANKED_SOLO_5x5", "PLATINUM", "v1"
        );
        verify(completionRepository, never()).insertIfAbsent(
                "KR_1", "p-gold", "RANKED_SOLO_5x5", "PLATINUM", "v1"
        );
        verify(aggregationService).aggregate(
                normalized,
                "PLATINUM",
                List.of("p-platinum-1", "p-platinum-2")
        );
    }

    @Test
    void 지정한_티어의_참가자가_없으면_선점하거나_집계하지_않는다() {
        NormalizedMatch normalized = normalizedMatch(
                participant("p-gold", 1, "GOLD"),
                participant("p-silver", 2, "SILVER")
        );

        matchService.registerMatchStats(normalized, "PLATINUM");

        verifyNoInteractions(completionRepository, aggregationService);
    }

    private NormalizedMatch normalizedMatch(NormalizedParticipant... participants) {
        return new NormalizedMatch("KR_1", "16.15", 420, List.of(participants));
    }

    private NormalizedParticipant participant(String puuid, int participantId, String tier) {
        return new NormalizedParticipant(
                puuid,
                participantId,
                participantId,
                100,
                "TOP",
                tier,
                true,
                List.of(3071),
                List.of(3071),
                true
        );
    }
}
