package dfgg.application.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dfgg.application.ChampionPositionNormalizer;
import dfgg.application.utils.WilsonScoreCalculator;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import dfgg.domain.sequence.MinedSequentialPattern;
import dfgg.domain.sequence.MinedSequentialPatternRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SafeZoneCandidateGeneratorTest {

    private static final String PATTERN_ALGORITHM_VERSION = "checkpoint-d-1";
    private static final int ANCHORED_PREFIX_LIMIT = 2;

    private NormalizedMatchParticipantRepository participantRepository;
    private MinedSequentialPatternRepository patternRepository;
    private SafeZoneCandidateGenerator safeZoneCandidateGenerator;

    @BeforeEach
    void setUp() {
        participantRepository = mock(NormalizedMatchParticipantRepository.class);
        patternRepository = mock(MinedSequentialPatternRepository.class);
        safeZoneCandidateGenerator = new SafeZoneCandidateGenerator(
                participantRepository, patternRepository,
                new ChampionPositionNormalizer(), new WilsonScoreCalculator()
        );
    }

    private List<RankedItemCandidate> rank(List<Long> purchasedItemIds) {
        return safeZoneCandidateGenerator.rankNextItemCandidates(
                purchasedItemIds, 222L, ChampionPosition.BOTTOM, "PLATINUM", "16.16",
                PATTERN_ALGORITHM_VERSION, ANCHORED_PREFIX_LIMIT
        );
    }

    // ── 집계 행 → 점수 매핑 (컬럼 순서가 조용히 뒤바뀌는 걸 막는다) ──

    @Test
    @DisplayName("집계 행의 2번째 열은 표본수, 3번째 열은 승수로 해석해 Wilson 하한을 계산한다")
    void rankNextItemCandidates_WhenMappingDistributionRow_ReadsSecondColumnAsSupportAndThirdAsWinCount() {
        // given: 표본 100 중 40승 → Wilson 하한 0.30939974...
        //        두 열을 뒤바꾸면 successes > total이라 NaN이 되고,
        //        승수를 표본수로 뭉개면 승률 100%가 되어 0.96300...으로 벌어진다.
        //        count(*)/sum(...)은 Postgres에서 bigint로 오므로 Long으로 넘겨 실제 타입도 함께 검증한다.
        when(participantRepository.findNextItemDistribution(
                222L, List.of("BOTTOM"), "16.16", "", 1
        )).thenReturn(List.<Object[]>of(new Object[]{"3031", 100L, 40L}));

        // when
        List<RankedItemCandidate> ranked = rank(List.of());

        // then
        assertThat(ranked).singleElement().satisfies(candidate -> {
            assertThat(candidate.itemId()).isEqualTo(3031L);
            assertThat(candidate.score()).isCloseTo(0.3093997461136029, within(1e-12));
        });
    }

    @Test
    @DisplayName("표본이 10배 커도 승률이 낮으면 승률 높은 후보보다 뒤로 밀린다")
    void rankNextItemCandidates_WhenLargeSampleHasLowWinRate_RanksHighWinRateCandidateFirst() {
        // given: 승률 낮은 쪽(1000표본 30% → 0.2724)을 일부러 먼저 반환한다.
        //        매핑이 어긋나 점수가 NaN이 되면 정렬이 입력 순서를 그대로 두므로 이 순서가 남는다.
        when(participantRepository.findNextItemDistribution(
                222L, List.of("BOTTOM"), "16.16", "", 1
        )).thenReturn(List.of(
                new Object[]{"3006", 1000L, 300L},
                new Object[]{"3031", 100L, 90L}
        ));

        // when
        List<RankedItemCandidate> ranked = rank(List.of());

        // then: 100표본 90%(0.8256)가 1000표본 30%(0.2724)를 앞선다
        assertThat(ranked).extracting(RankedItemCandidate::itemId).containsExactly(3031L, 3006L);
        assertThat(ranked).extracting(RankedItemCandidate::score).doesNotContain(Double.NaN);
    }

    // ── anchoredPrefixLimit(2) 미만 — 실제 구매 순서에 정확히 anchoring된 원본 집계 ──

    @Test
    @DisplayName("이미 산 아이템이 anchoredPrefixLimit보다 적으면(1코어) 실제 구매 순서 데이터로 랭킹한다")
    void rankNextItemCandidates_WhenBelowAnchoredPrefixLimit_UsesActualPurchaseOrderData() {
        // given: 3031은 표본 크기가 커서(1000명 중 600승) 승률은 같아도 Wilson 하한이 더 높다
        when(participantRepository.findNextItemDistribution(
                222L, List.of("BOTTOM"), "16.16", "", 1
        )).thenReturn(List.of(
                new Object[]{"3006", 10, 6},
                new Object[]{"3031", 1000, 600}
        ));

        // when
        List<RankedItemCandidate> ranked = rank(List.of());

        // then
        assertThat(ranked).extracting(RankedItemCandidate::itemId).containsExactly(3031L, 3006L);
    }

    @Test
    @DisplayName("이미 산 아이템 1개(2코어 추천)도 anchoredPrefixLimit 미만이면 정확한 prefix로 원본 데이터를 조회한다")
    void rankNextItemCandidates_WhenOnePurchasedItem_QueriesActualDataWithExactPrefix() {
        // given
        when(participantRepository.findNextItemDistribution(
                222L, List.of("BOTTOM"), "16.16", "3031", 2
        )).thenReturn(List.<Object[]>of(new Object[]{"3072", 50, 30}));

        // when
        List<RankedItemCandidate> ranked = rank(List.of(3031L));

        // then
        assertThat(ranked).extracting(RankedItemCandidate::itemId).containsExactly(3072L);
    }

    @Test
    @DisplayName("MID 포지션은 Riot 원시값 MIDDLE까지 조회 대상에 포함한다")
    void rankNextItemCandidates_WhenPositionIsMid_QueriesWithRiotAliasIncluded() {
        // given
        when(participantRepository.findNextItemDistribution(
                any(), any(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt()
        )).thenReturn(List.of());

        // when
        safeZoneCandidateGenerator.rankNextItemCandidates(
                List.of(), 103L, ChampionPosition.MID, "PLATINUM", "16.16",
                PATTERN_ALGORITHM_VERSION, ANCHORED_PREFIX_LIMIT
        );

        // then
        org.mockito.ArgumentCaptor<List<String>> positionsCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(participantRepository).findNextItemDistribution(
                org.mockito.ArgumentMatchers.eq(103L), positionsCaptor.capture(),
                anyString(), anyString(), org.mockito.ArgumentMatchers.eq(1)
        );
        assertThat(positionsCaptor.getValue()).containsExactlyInAnyOrder("MID", "MIDDLE");
    }

    @Test
    @DisplayName("실제 구매 순서 데이터가 없으면 빈 리스트를 반환한다")
    void rankNextItemCandidates_WhenNoActualPurchaseData_ReturnsEmptyList() {
        // given
        when(participantRepository.findNextItemDistribution(
                anyLong(), any(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt()
        )).thenReturn(List.of());

        // when
        List<RankedItemCandidate> ranked = rank(List.of());

        // then
        assertThat(ranked).isEmpty();
    }

    // ── anchoredPrefixLimit(2) 이상 — gap 허용 PrefixSpan 마이닝 결과 ──

    private MinedSequentialPattern patternEndingWith(List<Long> items, int supportCount, int winCount) {
        return new MinedSequentialPattern(
                222L, ChampionPosition.BOTTOM, "PLATINUM", "16.16", items, supportCount, 1000, winCount,
                PATTERN_ALGORITHM_VERSION
        );
    }

    @Test
    @DisplayName("이미 산 아이템이 anchoredPrefixLimit 이상이면(3코어~) 마이닝된 패턴에서 정확히 이어지는 것만 랭킹한다")
    void rankNextItemCandidates_WhenAtOrAboveAnchoredPrefixLimit_UsesMinedPatterns() {
        // given: prefix=[3031,3072]는 길이 2 → anchoredPrefixLimit(2) 이상이라 마이닝 패턴 사용
        MinedSequentialPattern extendsPrefix =
                patternEndingWith(List.of(3031L, 3072L, 3006L), 100, 60);
        MinedSequentialPattern differentPrefix =
                patternEndingWith(List.of(3020L, 3072L, 3006L), 200, 150);
        when(patternRepository.findByAlgorithmVersionAndChampionIdAndPositionAndTierAndPatch(
                PATTERN_ALGORITHM_VERSION, 222L, ChampionPosition.BOTTOM, "PLATINUM", "16.16"
        )).thenReturn(List.of(extendsPrefix, differentPrefix));

        // when
        List<RankedItemCandidate> ranked = rank(List.of(3031L, 3072L));

        // then
        assertThat(ranked).extracting(RankedItemCandidate::itemId).containsExactly(3006L);
    }

    @Test
    @DisplayName("마이닝 경로에서 이 prefix를 정확히 이어가는 패턴이 없으면 빈 리스트를 반환한다")
    void rankNextItemCandidates_WhenMinedPathHasNoMatchingPattern_ReturnsEmptyList() {
        // given
        when(patternRepository.findByAlgorithmVersionAndChampionIdAndPositionAndTierAndPatch(
                PATTERN_ALGORITHM_VERSION, 222L, ChampionPosition.BOTTOM, "PLATINUM", "16.16"
        )).thenReturn(List.of());

        // when
        List<RankedItemCandidate> ranked = rank(List.of(3031L, 3072L));

        // then
        assertThat(ranked).isEmpty();
    }
}
