package dfgg.application.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dfgg.application.utils.WilsonScoreCalculator;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.sequence.MinedSequentialPattern;
import dfgg.domain.sequence.MinedSequentialPatternRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SafeZoneCandidateGeneratorTest {

    private MinedSequentialPatternRepository patternRepository;
    private SafeZoneCandidateGenerator safeZoneCandidateGenerator;

    @BeforeEach
    void setUp() {
        patternRepository = mock(MinedSequentialPatternRepository.class);
        safeZoneCandidateGenerator = new SafeZoneCandidateGenerator(patternRepository, new WilsonScoreCalculator());
    }

    @Test
    @DisplayName("표본이 많아 신뢰도가 높은 패턴이 승률은 같아도 더 상위로 랭크된다")
    void rankByWilsonScore_WhenSameWinRateButDifferentSampleSize_RanksLargerSampleHigher() {
        // given
        MinedSequentialPattern smallSample = new MinedSequentialPattern(
                1L, ChampionPosition.BOTTOM, "PLATINUM", "16.16", List.of(3031L), 10, 100, 6, "checkpoint-d-1"
        );
        MinedSequentialPattern largeSample = new MinedSequentialPattern(
                1L, ChampionPosition.BOTTOM, "PLATINUM", "16.16", List.of(3072L), 1000, 100, 600, "checkpoint-d-1"
        );
        when(patternRepository.findByAlgorithmVersionAndChampionIdAndPositionAndTierAndPatch(
                "checkpoint-d-1", 1L, ChampionPosition.BOTTOM, "PLATINUM", "16.16"
        )).thenReturn(List.of(smallSample, largeSample));

        // when
        List<RankedSequentialPattern> ranked = safeZoneCandidateGenerator.rankByWilsonScore(
                1L, ChampionPosition.BOTTOM, "PLATINUM", "16.16", "checkpoint-d-1"
        );

        // then
        assertThat(ranked).extracting(candidate -> candidate.pattern().getItems())
                .containsExactly(List.of(3072L), List.of(3031L));
        assertThat(ranked.get(0).wilsonLowerBound()).isGreaterThan(ranked.get(1).wilsonLowerBound());
    }

    @Test
    @DisplayName("해당 스코프에 마이닝된 패턴이 없으면 빈 리스트를 반환한다")
    void rankByWilsonScore_WhenNoPatternsInScope_ReturnsEmptyList() {
        // given
        when(patternRepository.findByAlgorithmVersionAndChampionIdAndPositionAndTierAndPatch(
                anyString(), anyLong(), any(), anyString(), anyString()
        )).thenReturn(List.of());

        // when
        List<RankedSequentialPattern> ranked = safeZoneCandidateGenerator.rankByWilsonScore(
                999L, ChampionPosition.TOP, "GOLD", "16.16", "checkpoint-d-1"
        );

        // then
        assertThat(ranked).isEmpty();
    }
}
