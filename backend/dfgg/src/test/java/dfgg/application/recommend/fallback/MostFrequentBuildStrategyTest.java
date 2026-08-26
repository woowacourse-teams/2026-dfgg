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

    private RecommendationContext contextOf(Long championId, ChampionPosition position) {
        return new RecommendationContext(championId, position, "PLATINUM", "16.16", List.of(), List.of());
    }

    @Test
    @DisplayName("최다빈도 빌드 문자열을 아이템 ID 목록으로 구매 순서 그대로 변환한다")
    void recommend_WhenMostFrequentBuildExists_ParsesItIntoOrderedItemIds() {
        // given
        when(participantRepository.findMostFrequentBuild(anyLong(), any()))
                .thenReturn(Optional.of("3031,3072,3006"));

        // when
        Optional<List<Long>> itemIds = strategy.recommend(contextOf(222L, ChampionPosition.BOTTOM));

        // then
        assertThat(itemIds).isPresent();
        assertThat(itemIds.get()).containsExactly(3031L, 3072L, 3006L);
    }

    @Test
    @DisplayName("MID 포지션은 Riot 원시값 MIDDLE까지 조회 대상에 포함해 리포지토리를 호출한다")
    void recommend_WhenPositionIsMid_QueriesWithRiotAliasesIncluded() {
        // given
        when(participantRepository.findMostFrequentBuild(eq(103L), any()))
                .thenReturn(Optional.of("3020"));

        // when
        strategy.recommend(contextOf(103L, ChampionPosition.MID));

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
        Optional<List<Long>> itemIds = strategy.recommend(contextOf(99999L, ChampionPosition.TOP));

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
