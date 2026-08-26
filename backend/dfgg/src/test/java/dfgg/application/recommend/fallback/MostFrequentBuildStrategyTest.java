package dfgg.application.recommend.fallback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dfgg.application.ChampionPositionNormalizer;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MostFrequentBuildStrategyTest {

    private NormalizedMatchParticipantRepository participantRepository;
    private MostFrequentBuildStrategy strategy;

    @BeforeEach
    void setUp() {
        participantRepository = mock(NormalizedMatchParticipantRepository.class);
        strategy = new MostFrequentBuildStrategy(participantRepository, new ChampionPositionNormalizer());
    }

    private RecommendationContext contextOf(Long championId, ChampionPosition position, List<Long> purchasedItemIds) {
        return new RecommendationContext(
                championId, purchasedItemIds, position, "PLATINUM", "16.16", List.of(), List.of()
        );
    }

    @Test
    @DisplayName("아직 아무것도 안 샀으면(콜드스타트) 최다빈도 빌드의 첫 아이템 하나만 다음 아이템으로 추천한다")
    void recommend_WhenNoPurchasedItems_RecommendsFirstItemOfMostFrequentBuild() {
        // given
        when(participantRepository.findMostFrequentBuild(anyLong(), any()))
                .thenReturn(Optional.of("3031,3072,3006"));

        // when
        Optional<List<Long>> itemIds = strategy.recommend(contextOf(222L, ChampionPosition.BOTTOM, List.of()));

        // then
        assertThat(itemIds).contains(List.of(3031L));
    }

    @Test
    @DisplayName("이미 산 아이템 개수만큼 최다빈도 빌드에서 인덱스로 다음 아이템 하나만 추천한다")
    void recommend_WhenSomeItemsAlreadyPurchased_RecommendsNextItemByPurchasedCount() {
        // given: 최다빈도 빌드는 '3031,3072,3006', 이미 3031을 샀으니(1개) 다음은 인덱스 1인 3072
        when(participantRepository.findMostFrequentBuild(anyLong(), any()))
                .thenReturn(Optional.of("3031,3072,3006"));

        // when
        Optional<List<Long>> itemIds = strategy.recommend(contextOf(222L, ChampionPosition.BOTTOM, List.of(3031L)));

        // then
        assertThat(itemIds).contains(List.of(3072L));
    }

    @Test
    @DisplayName("이미 산 아이템 수가 최다빈도 빌드 길이 이상이면(빌드 완료) 빈 Optional을 반환한다")
    void recommend_WhenPurchasedItemCountReachesBuildLength_ReturnsEmptyOptional() {
        // given: 최다빈도 빌드는 아이템 2개뿐인데 이미 2개를 다 샀음
        when(participantRepository.findMostFrequentBuild(anyLong(), any()))
                .thenReturn(Optional.of("3031,3072"));

        // when
        Optional<List<Long>> itemIds = strategy.recommend(
                contextOf(222L, ChampionPosition.BOTTOM, List.of(3031L, 3072L))
        );

        // then
        assertThat(itemIds).isEmpty();
    }

    @Test
    @DisplayName("MID 포지션은 Riot 원시값 MIDDLE까지 조회 대상에 포함해 리포지토리를 호출한다")
    void recommend_WhenPositionIsMid_QueriesWithRiotAliasesIncluded() {
        // given
        when(participantRepository.findMostFrequentBuild(eq(103L), any()))
                .thenReturn(Optional.of("3020"));

        // when
        strategy.recommend(contextOf(103L, ChampionPosition.MID, List.of()));

        // then: 별칭을 안 넘기면 MIDDLE로 저장된 실 데이터를 한 건도 못 찾는다
        org.mockito.ArgumentCaptor<List<String>> positionsCaptor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(participantRepository)
                .findMostFrequentBuild(eq(103L), positionsCaptor.capture());
        assertThat(positionsCaptor.getValue()).containsExactlyInAnyOrder("MID", "MIDDLE");
    }

    @Test
    @DisplayName("최다빈도 빌드가 없으면 빈 Optional을 반환해 체인이 더 내려가게 한다")
    void recommend_WhenNoBuildFound_ReturnsEmptyOptional() {
        // given
        when(participantRepository.findMostFrequentBuild(anyLong(), any())).thenReturn(Optional.empty());

        // when
        Optional<List<Long>> itemIds = strategy.recommend(contextOf(99999L, ChampionPosition.TOP, List.of()));

        // then
        assertThat(itemIds).isEmpty();
    }

    @Test
    @DisplayName("자신이 담당하는 폴백 단계를 알려준다")
    void stage_ReturnsMostFrequentBuildStage() {
        // given & when & then
        assertThat(strategy.stage()).isEqualTo(FallbackStage.MOST_FREQUENT_BUILD);
    }
}
