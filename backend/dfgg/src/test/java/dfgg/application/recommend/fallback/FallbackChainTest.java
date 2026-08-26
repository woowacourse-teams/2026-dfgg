package dfgg.application.recommend.fallback;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.champion.ChampionPosition;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FallbackChainTest {

    private static final RecommendationContext CONTEXT = new RecommendationContext(
            222L, List.of(), ChampionPosition.BOTTOM, "PLATINUM", "16.16", List.of(412L), List.of(54L)
    );

    /**
     * 지정한 결과를 그대로 돌려주는 가짜 전략. 호출 여부를 기록해 뒷 단계가 불필요하게
     * 실행되지 않는지도 확인할 수 있게 한다.
     */
    private static class FakeStrategy implements RecommendationStrategy {

        private final FallbackStage stage;
        private final List<Long> itemIds;
        private boolean called;

        private FakeStrategy(FallbackStage stage, List<Long> itemIds) {
            this.stage = stage;
            this.itemIds = itemIds;
        }

        @Override
        public FallbackStage stage() {
            return stage;
        }

        @Override
        public Optional<List<Long>> recommend(RecommendationContext context) {
            called = true;
            return itemIds.isEmpty() ? Optional.empty() : Optional.of(itemIds);
        }
    }

    @Test
    @DisplayName("첫 단계가 결과를 내면 그 결과를 반환하고 어느 단계에서 서빙됐는지 함께 알려준다")
    void recommend_WhenFirstStageSucceeds_ReturnsItsResultWithServedStage() {
        // given
        FakeStrategy primary = new FakeStrategy(FallbackStage.PRIMARY, List.of(3031L, 3072L));
        FallbackChain chain = new FallbackChain(List.of(primary));

        // when
        Optional<FallbackRecommendation> recommendation = chain.recommend(CONTEXT);

        // then
        assertThat(recommendation).isPresent();
        assertThat(recommendation.get().itemIds()).containsExactly(3031L, 3072L);
        assertThat(recommendation.get().servedBy()).isEqualTo(FallbackStage.PRIMARY);
    }

    @Test
    @DisplayName("앞 단계가 빈 결과를 내면 다음 단계로 내려간다")
    void recommend_WhenEarlierStageIsEmpty_FallsThroughToNextStage() {
        // given
        FakeStrategy primary = new FakeStrategy(FallbackStage.PRIMARY, List.of());
        FakeStrategy compositionStats = new FakeStrategy(FallbackStage.COMPOSITION_STATS, List.of(3006L));
        FallbackChain chain = new FallbackChain(List.of(primary, compositionStats));

        // when
        Optional<FallbackRecommendation> recommendation = chain.recommend(CONTEXT);

        // then
        assertThat(recommendation).isPresent();
        assertThat(recommendation.get().itemIds()).containsExactly(3006L);
        assertThat(recommendation.get().servedBy()).isEqualTo(FallbackStage.COMPOSITION_STATS);
    }

    @Test
    @DisplayName("앞 단계가 성공하면 뒷 단계는 아예 실행하지 않는다")
    void recommend_WhenEarlierStageSucceeds_DoesNotRunLaterStages() {
        // given
        FakeStrategy primary = new FakeStrategy(FallbackStage.PRIMARY, List.of(3031L));
        FakeStrategy mostFrequent = new FakeStrategy(FallbackStage.MOST_FREQUENT_BUILD, List.of(3006L));
        FallbackChain chain = new FallbackChain(List.of(primary, mostFrequent));

        // when
        chain.recommend(CONTEXT);

        // then
        assertThat(primary.called).isTrue();
        assertThat(mostFrequent.called).isFalse();
    }

    @Test
    @DisplayName("모든 단계가 빈 결과를 내면 빈 Optional을 반환한다")
    void recommend_WhenAllStagesAreEmpty_ReturnsEmptyOptional() {
        // given
        FakeStrategy primary = new FakeStrategy(FallbackStage.PRIMARY, List.of());
        FakeStrategy mostFrequent = new FakeStrategy(FallbackStage.MOST_FREQUENT_BUILD, List.of());
        FallbackChain chain = new FallbackChain(List.of(primary, mostFrequent));

        // when
        Optional<FallbackRecommendation> recommendation = chain.recommend(CONTEXT);

        // then
        assertThat(recommendation).isEmpty();
    }

    @Test
    @DisplayName("등록된 순서대로 단계를 시도한다")
    void recommend_WhenMultipleStagesRegistered_TriesThemInRegisteredOrder() {
        // given: 앞의 두 단계는 비어 있고 세 번째 단계만 결과를 낸다
        FakeStrategy primary = new FakeStrategy(FallbackStage.PRIMARY, List.of());
        FakeStrategy compositionStats = new FakeStrategy(FallbackStage.COMPOSITION_STATS, List.of());
        FakeStrategy mostFrequent = new FakeStrategy(FallbackStage.MOST_FREQUENT_BUILD, List.of(3006L));
        FallbackChain chain = new FallbackChain(List.of(primary, compositionStats, mostFrequent));

        // when
        Optional<FallbackRecommendation> recommendation = chain.recommend(CONTEXT);

        // then
        assertThat(primary.called).isTrue();
        assertThat(compositionStats.called).isTrue();
        assertThat(recommendation.orElseThrow().servedBy()).isEqualTo(FallbackStage.MOST_FREQUENT_BUILD);
    }
}
