package dfgg.application.recommend.fallback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dfgg.application.recommend.RecommendationBuildComposer;
import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.champion.ChampionRepository;
import dfgg.domain.item.Item;
import dfgg.domain.stats.ChampionBuildStats;
import dfgg.domain.stats.ChampionBuildStatsRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CompositionStatsStrategyTest {

    private ChampionRepository championRepository;
    private ChampionBuildStatsRepository statsRepository;
    private RecommendationBuildComposer buildComposer;
    private CompositionStatsStrategy strategy;

    @BeforeEach
    void setUp() {
        championRepository = mock(ChampionRepository.class);
        statsRepository = mock(ChampionBuildStatsRepository.class);
        buildComposer = mock(RecommendationBuildComposer.class);
        strategy = new CompositionStatsStrategy(championRepository, statsRepository, buildComposer);
        when(championRepository.findAllById(any())).thenReturn(List.of());
    }

    private RecommendationContext contextOf(List<Long> purchasedItemIds) {
        return new RecommendationContext(
                222L, purchasedItemIds, ChampionPosition.BOTTOM, "PLATINUM", "16.16", List.of(412L), List.of(54L)
        );
    }

    @Test
    @DisplayName("아직 아무것도 안 샀으면(콜드스타트) 조합된 빌드의 첫 아이템 하나만 다음 아이템으로 추천한다")
    void recommend_WhenNoPurchasedItems_RecommendsFirstItemOfComposedBuild() {
        // given
        when(statsRepository.findAllMatchingStats(
                anyLong(), anyString(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean()
        )).thenReturn(List.of(mock(ChampionBuildStats.class)));
        when(buildComposer.compose(any(), any())).thenReturn(List.of(
                new Item(3006L, "광전사의 군화"),
                new Item(3031L, "무한의 대검")
        ));

        // when
        Optional<List<Long>> itemIds = strategy.recommend(contextOf(List.of()));

        // then
        assertThat(itemIds).contains(List.of(3006L));
    }

    @Test
    @DisplayName("이미 산 아이템 개수만큼 조합된 빌드에서 인덱스로 다음 아이템 하나만 추천한다")
    void recommend_WhenSomeItemsAlreadyPurchased_RecommendsNextItemByPurchasedCount() {
        // given: 조합된 빌드는 [3006, 3031], 이미 3006을 샀으니(1개) 다음은 인덱스 1인 3031
        when(statsRepository.findAllMatchingStats(
                anyLong(), anyString(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean()
        )).thenReturn(List.of(mock(ChampionBuildStats.class)));
        when(buildComposer.compose(any(), any())).thenReturn(List.of(
                new Item(3006L, "광전사의 군화"),
                new Item(3031L, "무한의 대검")
        ));

        // when
        Optional<List<Long>> itemIds = strategy.recommend(contextOf(List.of(3006L)));

        // then
        assertThat(itemIds).contains(List.of(3031L));
    }

    @Test
    @DisplayName("이미 산 아이템 수가 조합된 빌드 길이 이상이면(빌드 완료) 빈 Optional을 반환한다")
    void recommend_WhenPurchasedItemCountReachesComposedBuildLength_ReturnsEmptyOptional() {
        // given
        when(statsRepository.findAllMatchingStats(
                anyLong(), anyString(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean()
        )).thenReturn(List.of(mock(ChampionBuildStats.class)));
        when(buildComposer.compose(any(), any())).thenReturn(List.of(new Item(3006L, "광전사의 군화")));

        // when
        Optional<List<Long>> itemIds = strategy.recommend(contextOf(List.of(3006L)));

        // then
        assertThat(itemIds).isEmpty();
    }

    @Test
    @DisplayName("매칭되는 통계가 없으면 빈 Optional을 반환하고 조합은 시도하지 않는다")
    void recommend_WhenNoMatchingStats_ReturnsEmptyWithoutComposing() {
        // given
        when(statsRepository.findAllMatchingStats(
                anyLong(), anyString(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean()
        )).thenReturn(List.of());

        // when
        Optional<List<Long>> itemIds = strategy.recommend(contextOf(List.of()));

        // then
        assertThat(itemIds).isEmpty();
        verify(buildComposer, never()).compose(any(), any());
    }

    @Test
    @DisplayName("통계는 있지만 조합 결과가 비면 빈 Optional을 반환해 체인이 더 내려가게 한다")
    void recommend_WhenComposedBuildIsEmpty_ReturnsEmptyOptional() {
        // given: BOTTOM은 신발 후보가 없으면 빈 빌드가 나올 수 있다
        when(statsRepository.findAllMatchingStats(
                anyLong(), anyString(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean()
        )).thenReturn(List.of(mock(ChampionBuildStats.class)));
        when(buildComposer.compose(any(), any())).thenReturn(List.of());

        // when
        Optional<List<Long>> itemIds = strategy.recommend(contextOf(List.of()));

        // then
        assertThat(itemIds).isEmpty();
    }

    @Test
    @DisplayName("아군·적군 챔피언 조합을 분석한 다섯 가지 조건으로 통계를 조회한다")
    void recommend_WhenCalled_QueriesStatsWithAnalyzedCombinationConditions() {
        // given: 적군에 탱커 2명이 있으면 enemyTankHeavy가 true여야 한다
        Champion enemyTankOne = mock(Champion.class);
        Champion enemyTankTwo = mock(Champion.class);
        when(enemyTankOne.getChampionTags()).thenReturn(List.of(dfgg.domain.champion.ChampionTag.TANK));
        when(enemyTankTwo.getChampionTags()).thenReturn(List.of(dfgg.domain.champion.ChampionTag.TANK));
        when(championRepository.findAllById(List.of(54L, 517L))).thenReturn(List.of(enemyTankOne, enemyTankTwo));
        when(statsRepository.findAllMatchingStats(
                anyLong(), anyString(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean()
        )).thenReturn(List.of());

        RecommendationContext context = new RecommendationContext(
                222L, List.of(), ChampionPosition.BOTTOM, "PLATINUM", "16.16", List.of(), List.of(54L, 517L)
        );

        // when
        strategy.recommend(context);

        // then
        verify(statsRepository).findAllMatchingStats(
                222L, "BOTTOM", true, false, false, false, false
        );
    }

    @Test
    @DisplayName("자신이 담당하는 폴백 단계를 알려준다")
    void stage_ReturnsCompositionStatsStage() {
        // given & when & then
        assertThat(strategy.stage()).isEqualTo(FallbackStage.COMPOSITION_STATS);
    }
}
