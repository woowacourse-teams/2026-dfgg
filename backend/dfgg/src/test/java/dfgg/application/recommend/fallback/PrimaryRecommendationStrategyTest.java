package dfgg.application.recommend.fallback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dfgg.application.recommend.CandidateSimilarityScorer;
import dfgg.application.recommend.CandidateZoneMixer;
import dfgg.application.recommend.ExplorationZoneCandidateGenerator;
import dfgg.application.recommend.FinalScoreCalculator;
import dfgg.application.recommend.ItemSimilarityScores;
import dfgg.application.recommend.MixedCandidates;
import dfgg.application.recommend.RankedItemCandidate;
import dfgg.application.recommend.SafeZoneCandidateGenerator;
import dfgg.domain.champion.ChampionPosition;
import dfgg.infrastructure.config.RecommendationProperties;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PrimaryRecommendationStrategyTest {

    private SafeZoneCandidateGenerator safeZoneCandidateGenerator;
    private ExplorationZoneCandidateGenerator explorationZoneCandidateGenerator;
    private CandidateZoneMixer candidateZoneMixer;
    private CandidateSimilarityScorer candidateSimilarityScorer;
    private PrimaryRecommendationStrategy strategy;

    private static final RecommendationProperties PROPERTIES = new RecommendationProperties(
            "checkpoint-a-4", "checkpoint-c-1-counter", "checkpoint-d-1", 2,
            10, 5, 0.8, 1.0, 1.0, 1.0, 1.0
    );

    @BeforeEach
    void setUp() {
        safeZoneCandidateGenerator = mock(SafeZoneCandidateGenerator.class);
        explorationZoneCandidateGenerator = mock(ExplorationZoneCandidateGenerator.class);
        candidateZoneMixer = mock(CandidateZoneMixer.class);
        candidateSimilarityScorer = mock(CandidateSimilarityScorer.class);
        strategy = new PrimaryRecommendationStrategy(
                safeZoneCandidateGenerator,
                explorationZoneCandidateGenerator,
                candidateZoneMixer,
                candidateSimilarityScorer,
                new FinalScoreCalculator(),
                PROPERTIES
        );
    }

    private RecommendationContext contextOf(List<Long> purchasedItemIds) {
        return new RecommendationContext(
                222L, purchasedItemIds, ChampionPosition.BOTTOM, "PLATINUM", "16.16", List.of(412L), List.of(54L)
        );
    }

    @Test
    @DisplayName("안전 구역과 탐색 구역 후보를 합쳐 finalScore 내림차순으로 정렬한 아이템 목록을 반환한다")
    void recommend_WhenCandidatesExistInBothZones_ReturnsItemIdsSortedByFinalScoreDescending() {
        // given: 안전 구역 후보 3072(wilson=0.5), 탐색 구역 후보 3006(wilson 항 없음, 0으로 처리)
        RankedItemCandidate safeZoneCandidate = new RankedItemCandidate(3072L, 0.5);
        RankedItemCandidate explorationZoneCandidate = new RankedItemCandidate(3006L, 0.9);

        when(safeZoneCandidateGenerator.rankNextItemCandidates(
                anyList(), any(), any(), anyString(), anyString(), anyString(), anyInt()
        )).thenReturn(List.of(safeZoneCandidate));
        when(explorationZoneCandidateGenerator.rankByMaxSimilarityToEnemies(anyList(), anyString(), any(), any()))
                .thenReturn(List.of(explorationZoneCandidate));
        when(candidateZoneMixer.mix(List.of(safeZoneCandidate), List.of(explorationZoneCandidate), 10, 0.8))
                .thenReturn(new MixedCandidates(List.of(safeZoneCandidate), List.of(explorationZoneCandidate)));

        // 3072: cos=0.5, allySim=0.5, enemySim=0.1 → finalScore = 0.5(wilson)+0.5+0.5+0.1 = 1.6
        // 3006: cos=0.1, allySim=0.1, enemySim=0.9 → finalScore = 0(wilson 없음)+0.1+0.1+0.9 = 1.1
        when(candidateSimilarityScorer.scoreItems(
                List.of(3072L, 3006L), 222L, List.of(412L), List.of(54L), "checkpoint-a-4", "checkpoint-c-1-counter"
        )).thenReturn(List.of(
                new ItemSimilarityScores(3072L, 0.5, 0.5, 0.1),
                new ItemSimilarityScores(3006L, 0.1, 0.1, 0.9)
        ));

        // when
        Optional<List<Long>> result = strategy.recommend(contextOf(List.of(3031L)));

        // then
        assertThat(result).isPresent();
        assertThat(result.get()).containsExactly(3072L, 3006L);
    }

    @Test
    @DisplayName("두 구역 모두 후보가 없으면 빈 Optional을 반환해 체인이 다음 단계로 내려가게 한다")
    void recommend_WhenBothZonesHaveNoCandidates_ReturnsEmptyOptional() {
        // given
        when(safeZoneCandidateGenerator.rankNextItemCandidates(
                anyList(), any(), any(), anyString(), anyString(), anyString(), anyInt()
        )).thenReturn(List.of());
        when(explorationZoneCandidateGenerator.rankByMaxSimilarityToEnemies(anyList(), anyString(), any(), any()))
                .thenReturn(List.of());
        when(candidateZoneMixer.mix(List.of(), List.of(), 10, 0.8))
                .thenReturn(new MixedCandidates(List.of(), List.of()));

        // when
        Optional<List<Long>> result = strategy.recommend(contextOf(List.of()));

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("안전 구역과 탐색 구역에 같은 아이템이 겹치면 안전 구역의 Wilson 점수를 우선하고 중복 없이 한 번만 채택한다")
    void recommend_WhenSameItemAppearsInBothZones_PrefersSafeZoneWilsonScoreAndDeduplicates() {
        // given: 3072가 안전 구역(wilson=0.5)과 탐색 구역 둘 다에 등장, 3006은 탐색 구역에만 등장
        // 3072가 wilson=0.5를 제대로 쓰면 finalScore=0.5+0.2+0.2+0.2=1.1 > 3006의 0.8 → [3072, 3006]
        // 3072가 잘못 wilson=0으로 계산되면 finalScore=0.6 < 3006의 0.8 → [3006, 3072] (버그로 판별됨)
        RankedItemCandidate safeZoneCandidate = new RankedItemCandidate(3072L, 0.5);
        RankedItemCandidate explorationZoneOverlap = new RankedItemCandidate(3072L, 0.9);
        RankedItemCandidate explorationZoneUnique = new RankedItemCandidate(3006L, 0.5);

        when(safeZoneCandidateGenerator.rankNextItemCandidates(
                anyList(), any(), any(), anyString(), anyString(), anyString(), anyInt()
        )).thenReturn(List.of(safeZoneCandidate));
        when(explorationZoneCandidateGenerator.rankByMaxSimilarityToEnemies(anyList(), anyString(), any(), any()))
                .thenReturn(List.of(explorationZoneOverlap, explorationZoneUnique));
        when(candidateZoneMixer.mix(
                List.of(safeZoneCandidate), List.of(explorationZoneOverlap, explorationZoneUnique), 10, 0.8
        )).thenReturn(new MixedCandidates(
                List.of(safeZoneCandidate), List.of(explorationZoneOverlap, explorationZoneUnique)
        ));
        when(candidateSimilarityScorer.scoreItems(
                List.of(3072L, 3006L), 222L, List.of(412L), List.of(54L), "checkpoint-a-4", "checkpoint-c-1-counter"
        )).thenReturn(List.of(
                new ItemSimilarityScores(3072L, 0.2, 0.2, 0.2),
                new ItemSimilarityScores(3006L, 0.3, 0.3, 0.2)
        ));

        // when
        Optional<List<Long>> result = strategy.recommend(contextOf(List.of(3031L)));

        // then
        assertThat(result.get()).containsExactly(3072L, 3006L);
    }

    @Test
    @DisplayName("탐색 구역 후보가 이미 구매한 아이템이면 결과에서 제외한다")
    void recommend_WhenExplorationZoneCandidateAlreadyPurchased_ExcludesItFromResult() {
        // given: 6662는 이미 구매했다. 안전 구역(마이닝)은 prefix가 깊어져 매칭 패턴이 없고
        //        (실제 관측된 버그 재현: 깊은 prefix에서 안전 구역이 빈 채로 탐색 구역만 남음),
        //        탐색 구역은 prefix를 모르므로 이미 산 6662를 그대로 후보에 올린다.
        RankedItemCandidate alreadyPurchased = new RankedItemCandidate(6662L, 0.9);
        RankedItemCandidate notYetPurchased = new RankedItemCandidate(3047L, 0.5);

        when(safeZoneCandidateGenerator.rankNextItemCandidates(
                anyList(), any(), any(), anyString(), anyString(), anyString(), anyInt()
        )).thenReturn(List.of());
        when(explorationZoneCandidateGenerator.rankByMaxSimilarityToEnemies(anyList(), anyString(), any(), any()))
                .thenReturn(List.of(alreadyPurchased, notYetPurchased));
        when(candidateZoneMixer.mix(List.of(), List.of(alreadyPurchased, notYetPurchased), 10, 0.8))
                .thenReturn(new MixedCandidates(List.of(), List.of(alreadyPurchased, notYetPurchased)));
        when(candidateSimilarityScorer.scoreItems(
                List.of(3047L), 222L, List.of(412L), List.of(54L), "checkpoint-a-4", "checkpoint-c-1-counter"
        )).thenReturn(List.of(new ItemSimilarityScores(3047L, 0.3, 0.3, 0.3)));

        // when
        Optional<List<Long>> result = strategy.recommend(contextOf(List.of(3031L, 6662L)));

        // then
        assertThat(result.get()).containsExactly(3047L);
    }

    @Test
    @DisplayName("합친 후보 수가 임계값 이하이면 prefix를 한 칸 줄여 안전 구역 후보를 추가로 채운다")
    void recommend_WhenCandidateCountAtOrBelowThreshold_BacksOffPrefixToFillMoreCandidates() {
        // given: prefix=[3031,3072]로는 안전 구역 후보가 3006 하나뿐(임계값 5 이하) →
        //        한 칸 줄인 prefix=[3031]로 다시 조회해 5개를 더 채운다(총 6개, 5 초과라 종료).
        RankedItemCandidate preciseOnly = new RankedItemCandidate(3006L, 0.9);
        List<RankedItemCandidate> backoffExtras = List.of(
                new RankedItemCandidate(3008L, 0.8), new RankedItemCandidate(3020L, 0.7),
                new RankedItemCandidate(3047L, 0.6), new RankedItemCandidate(3111L, 0.5),
                new RankedItemCandidate(3158L, 0.4)
        );
        List<RankedItemCandidate> merged = List.of(
                preciseOnly, backoffExtras.get(0), backoffExtras.get(1),
                backoffExtras.get(2), backoffExtras.get(3), backoffExtras.get(4)
        );

        when(safeZoneCandidateGenerator.rankNextItemCandidates(
                eq(List.of(3031L, 3072L)), any(), any(), anyString(), anyString(), anyString(), anyInt()
        )).thenReturn(List.of(preciseOnly));
        when(safeZoneCandidateGenerator.rankNextItemCandidates(
                eq(List.of(3031L)), any(), any(), anyString(), anyString(), anyString(), anyInt()
        )).thenReturn(backoffExtras);
        when(explorationZoneCandidateGenerator.rankByMaxSimilarityToEnemies(anyList(), anyString(), any(), any()))
                .thenReturn(List.of());
        when(candidateZoneMixer.mix(List.of(preciseOnly), List.of(), 10, 0.8))
                .thenReturn(new MixedCandidates(List.of(preciseOnly), List.of()));
        when(candidateZoneMixer.mix(merged, List.of(), 10, 0.8))
                .thenReturn(new MixedCandidates(merged, List.of()));
        when(candidateSimilarityScorer.scoreItems(
                List.of(3006L, 3008L, 3020L, 3047L, 3111L, 3158L),
                222L, List.of(412L), List.of(54L), "checkpoint-a-4", "checkpoint-c-1-counter"
        )).thenReturn(List.of(
                new ItemSimilarityScores(3006L, 0.0, 0.0, 0.0), new ItemSimilarityScores(3008L, 0.0, 0.0, 0.0),
                new ItemSimilarityScores(3020L, 0.0, 0.0, 0.0), new ItemSimilarityScores(3047L, 0.0, 0.0, 0.0),
                new ItemSimilarityScores(3111L, 0.0, 0.0, 0.0), new ItemSimilarityScores(3158L, 0.0, 0.0, 0.0)
        ));

        // when
        Optional<List<Long>> result = strategy.recommend(contextOf(List.of(3031L, 3072L)));

        // then: Wilson 점수 내림차순 그대로(다른 항이 전부 0이라 순위가 바뀌지 않음)
        assertThat(result.get()).containsExactly(3006L, 3008L, 3020L, 3047L, 3111L, 3158L);
        verify(safeZoneCandidateGenerator).rankNextItemCandidates(
                eq(List.of(3031L, 3072L)), any(), any(), anyString(), anyString(), anyString(), anyInt()
        );
        verify(safeZoneCandidateGenerator).rankNextItemCandidates(
                eq(List.of(3031L)), any(), any(), anyString(), anyString(), anyString(), anyInt()
        );
    }

    @Test
    @DisplayName("완화된 prefix에서 같은 아이템이 다시 나와도 더 정밀한(원래 prefix) 점수를 우선한다")
    void recommend_WhenSameItemFoundAtBothPrefixDepths_PrefersDeeperPrefixScore() {
        // given: 3006이 정밀한 prefix에서는 점수 0.9, 완화된 prefix에서는 0.1로 다시 나온다.
        //        완화된 점수로 덮어써지면 3006이 맨 뒤로 밀리므로, 순서 자체가 어느 점수가
        //        채택됐는지를 그대로 증명한다.
        RankedItemCandidate preciseDuplicate = new RankedItemCandidate(3006L, 0.9);
        RankedItemCandidate backoffDuplicate = new RankedItemCandidate(3006L, 0.1);
        List<RankedItemCandidate> backoffExtras = List.of(
                backoffDuplicate,
                new RankedItemCandidate(3008L, 0.8), new RankedItemCandidate(3020L, 0.7),
                new RankedItemCandidate(3047L, 0.6), new RankedItemCandidate(3111L, 0.5)
        );
        // 3006은 precise 점수(0.9)를 그대로 유지한 채 정렬되어야 한다
        List<RankedItemCandidate> merged = List.of(
                preciseDuplicate, backoffExtras.get(1), backoffExtras.get(2),
                backoffExtras.get(3), backoffExtras.get(4)
        );

        when(safeZoneCandidateGenerator.rankNextItemCandidates(
                eq(List.of(3031L, 3072L)), any(), any(), anyString(), anyString(), anyString(), anyInt()
        )).thenReturn(List.of(preciseDuplicate));
        when(safeZoneCandidateGenerator.rankNextItemCandidates(
                eq(List.of(3031L)), any(), any(), anyString(), anyString(), anyString(), anyInt()
        )).thenReturn(backoffExtras);
        when(explorationZoneCandidateGenerator.rankByMaxSimilarityToEnemies(anyList(), anyString(), any(), any()))
                .thenReturn(List.of());
        when(candidateZoneMixer.mix(List.of(preciseDuplicate), List.of(), 10, 0.8))
                .thenReturn(new MixedCandidates(List.of(preciseDuplicate), List.of()));
        when(candidateZoneMixer.mix(merged, List.of(), 10, 0.8))
                .thenReturn(new MixedCandidates(merged, List.of()));
        when(candidateSimilarityScorer.scoreItems(
                List.of(3006L, 3008L, 3020L, 3047L, 3111L),
                222L, List.of(412L), List.of(54L), "checkpoint-a-4", "checkpoint-c-1-counter"
        )).thenReturn(List.of(
                new ItemSimilarityScores(3006L, 0.0, 0.0, 0.0), new ItemSimilarityScores(3008L, 0.0, 0.0, 0.0),
                new ItemSimilarityScores(3020L, 0.0, 0.0, 0.0), new ItemSimilarityScores(3047L, 0.0, 0.0, 0.0),
                new ItemSimilarityScores(3111L, 0.0, 0.0, 0.0)
        ));

        // when
        Optional<List<Long>> result = strategy.recommend(contextOf(List.of(3031L, 3072L)));

        // then: 3006이 0.9로 유지됐다면 1등, 0.1로 덮어써졌다면 꼴찌였을 것
        assertThat(result.get()).containsExactly(3006L, 3008L, 3020L, 3047L, 3111L);
    }

    @Test
    @DisplayName("합친 후보 수가 이미 임계값을 넘으면 prefix를 완화하지 않는다")
    void recommend_WhenCandidateCountAboveThreshold_DoesNotBackOffPrefix() {
        // given: 안전 구역만으로 이미 6개(임계값 5 초과)
        List<RankedItemCandidate> plenty = List.of(
                new RankedItemCandidate(3006L, 0.9), new RankedItemCandidate(3008L, 0.8),
                new RankedItemCandidate(3020L, 0.7), new RankedItemCandidate(3047L, 0.6),
                new RankedItemCandidate(3111L, 0.5), new RankedItemCandidate(3158L, 0.4)
        );

        when(safeZoneCandidateGenerator.rankNextItemCandidates(
                eq(List.of(3031L)), any(), any(), anyString(), anyString(), anyString(), anyInt()
        )).thenReturn(plenty);
        when(explorationZoneCandidateGenerator.rankByMaxSimilarityToEnemies(anyList(), anyString(), any(), any()))
                .thenReturn(List.of());
        when(candidateZoneMixer.mix(plenty, List.of(), 10, 0.8))
                .thenReturn(new MixedCandidates(plenty, List.of()));
        when(candidateSimilarityScorer.scoreItems(
                List.of(3006L, 3008L, 3020L, 3047L, 3111L, 3158L),
                222L, List.of(412L), List.of(54L), "checkpoint-a-4", "checkpoint-c-1-counter"
        )).thenReturn(List.of(
                new ItemSimilarityScores(3006L, 0.0, 0.0, 0.0), new ItemSimilarityScores(3008L, 0.0, 0.0, 0.0),
                new ItemSimilarityScores(3020L, 0.0, 0.0, 0.0), new ItemSimilarityScores(3047L, 0.0, 0.0, 0.0),
                new ItemSimilarityScores(3111L, 0.0, 0.0, 0.0), new ItemSimilarityScores(3158L, 0.0, 0.0, 0.0)
        ));

        // when
        strategy.recommend(contextOf(List.of(3031L)));

        // then: 완화된(더 짧은) prefix로는 한 번도 조회하지 않는다
        verify(safeZoneCandidateGenerator, times(1)).rankNextItemCandidates(
                anyList(), any(), any(), anyString(), anyString(), anyString(), anyInt()
        );
        verify(safeZoneCandidateGenerator, never()).rankNextItemCandidates(
                eq(List.of()), any(), any(), anyString(), anyString(), anyString(), anyInt()
        );
    }

    @Test
    @DisplayName("자신이 담당하는 폴백 단계를 알려준다")
    void stage_ReturnsPrimaryStage() {
        // given & when & then
        assertThat(strategy.stage()).isEqualTo(FallbackStage.PRIMARY);
    }
}
