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

    private static final String ALGORITHM_VERSION = "checkpoint-d-1";

    private MinedSequentialPatternRepository patternRepository;
    private SafeZoneCandidateGenerator safeZoneCandidateGenerator;

    @BeforeEach
    void setUp() {
        patternRepository = mock(MinedSequentialPatternRepository.class);
        safeZoneCandidateGenerator = new SafeZoneCandidateGenerator(patternRepository, new WilsonScoreCalculator());
    }

    private MinedSequentialPattern patternOf(List<Long> items, int supportCount, int winCount) {
        return new MinedSequentialPattern(
                1L, ChampionPosition.BOTTOM, "PLATINUM", "16.16",
                items, supportCount, 1000, winCount, ALGORITHM_VERSION
        );
    }

    @Test
    @DisplayName("이미 산 아이템이 없으면(콜드스타트) 길이가 1인 패턴만 다음 아이템 후보로 삼는다")
    void rankNextItemCandidates_WhenPurchasedItemsIsEmpty_ConsidersOnlyLengthOnePatterns() {
        // given
        MinedSequentialPattern firstItemCandidate = patternOf(List.of(3031L), 100, 60);
        MinedSequentialPattern twoItemBuild = patternOf(List.of(3031L, 3072L), 50, 30);
        when(patternRepository.findByAlgorithmVersionAndChampionIdAndPositionAndTierAndPatch(
                ALGORITHM_VERSION, 1L, ChampionPosition.BOTTOM, "PLATINUM", "16.16"
        )).thenReturn(List.of(firstItemCandidate, twoItemBuild));

        // when
        List<RankedSequentialPattern> ranked = safeZoneCandidateGenerator.rankNextItemCandidates(
                List.of(), 1L, ChampionPosition.BOTTOM, "PLATINUM", "16.16", ALGORITHM_VERSION
        );

        // then
        assertThat(ranked).extracting(candidate -> candidate.pattern().getItems())
                .containsExactly(List.of(3031L));
    }

    @Test
    @DisplayName("이미 산 아이템 뒤에 정확히 하나 더 붙는 패턴만 다음 아이템 후보로 삼는다")
    void rankNextItemCandidates_WhenPurchasedItemsGiven_OnlyConsidersPatternsExtendingThatExactPrefix() {
        // given
        MinedSequentialPattern extendsPrefixA = patternOf(List.of(3031L, 3072L), 100, 60);
        MinedSequentialPattern extendsPrefixB = patternOf(List.of(3031L, 3006L), 80, 40);
        MinedSequentialPattern differentPrefix = patternOf(List.of(3020L, 3072L), 200, 150);
        MinedSequentialPattern tooShort = patternOf(List.of(3031L), 100, 60);
        MinedSequentialPattern tooLong = patternOf(List.of(3031L, 3072L, 3006L), 100, 60);
        when(patternRepository.findByAlgorithmVersionAndChampionIdAndPositionAndTierAndPatch(
                ALGORITHM_VERSION, 1L, ChampionPosition.BOTTOM, "PLATINUM", "16.16"
        )).thenReturn(List.of(extendsPrefixA, extendsPrefixB, differentPrefix, tooShort, tooLong));

        // when
        List<RankedSequentialPattern> ranked = safeZoneCandidateGenerator.rankNextItemCandidates(
                List.of(3031L), 1L, ChampionPosition.BOTTOM, "PLATINUM", "16.16", ALGORITHM_VERSION
        );

        // then
        assertThat(ranked).extracting(candidate -> candidate.pattern().getItems())
                .containsExactlyInAnyOrder(List.of(3031L, 3072L), List.of(3031L, 3006L));
    }

    @Test
    @DisplayName("표본이 많아 신뢰도가 높은 다음 아이템 후보가 승률은 같아도 더 상위로 랭크된다")
    void rankNextItemCandidates_WhenSameWinRateButDifferentSampleSize_RanksLargerSampleHigher() {
        // given
        MinedSequentialPattern smallSample = patternOf(List.of(3031L, 3006L), 10, 6);
        MinedSequentialPattern largeSample = patternOf(List.of(3031L, 3072L), 1000, 600);
        when(patternRepository.findByAlgorithmVersionAndChampionIdAndPositionAndTierAndPatch(
                ALGORITHM_VERSION, 1L, ChampionPosition.BOTTOM, "PLATINUM", "16.16"
        )).thenReturn(List.of(smallSample, largeSample));

        // when
        List<RankedSequentialPattern> ranked = safeZoneCandidateGenerator.rankNextItemCandidates(
                List.of(3031L), 1L, ChampionPosition.BOTTOM, "PLATINUM", "16.16", ALGORITHM_VERSION
        );

        // then
        assertThat(ranked).extracting(candidate -> candidate.pattern().getItems())
                .containsExactly(List.of(3031L, 3072L), List.of(3031L, 3006L));
        assertThat(ranked.get(0).wilsonLowerBound()).isGreaterThan(ranked.get(1).wilsonLowerBound());
    }

    @Test
    @DisplayName("이 prefix를 정확히 이어가는 패턴이 하나도 없으면 빈 리스트를 반환한다")
    void rankNextItemCandidates_WhenNoPatternExtendsThisExactPrefix_ReturnsEmptyList() {
        // given: 스코프에 패턴은 있지만 이 prefix로 시작하는 게 하나도 없다
        MinedSequentialPattern differentPrefix = patternOf(List.of(3020L, 3072L), 200, 150);
        when(patternRepository.findByAlgorithmVersionAndChampionIdAndPositionAndTierAndPatch(
                ALGORITHM_VERSION, 1L, ChampionPosition.BOTTOM, "PLATINUM", "16.16"
        )).thenReturn(List.of(differentPrefix));

        // when
        List<RankedSequentialPattern> ranked = safeZoneCandidateGenerator.rankNextItemCandidates(
                List.of(3031L), 1L, ChampionPosition.BOTTOM, "PLATINUM", "16.16", ALGORITHM_VERSION
        );

        // then
        assertThat(ranked).isEmpty();
    }

    @Test
    @DisplayName("해당 스코프에 마이닝된 패턴이 없으면 빈 리스트를 반환한다")
    void rankNextItemCandidates_WhenNoPatternsInScope_ReturnsEmptyList() {
        // given
        when(patternRepository.findByAlgorithmVersionAndChampionIdAndPositionAndTierAndPatch(
                anyString(), anyLong(), any(), anyString(), anyString()
        )).thenReturn(List.of());

        // when
        List<RankedSequentialPattern> ranked = safeZoneCandidateGenerator.rankNextItemCandidates(
                List.of(), 999L, ChampionPosition.TOP, "GOLD", "16.16", ALGORITHM_VERSION
        );

        // then
        assertThat(ranked).isEmpty();
    }
}
