package dfgg.application.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dfgg.application.champion.ChampionService;
import dfgg.application.item.ItemService;
import dfgg.application.recommend.fallback.FallbackChain;
import dfgg.application.recommend.fallback.FallbackRecommendation;
import dfgg.application.recommend.fallback.FallbackStage;
import dfgg.application.recommend.fallback.RecommendationContext;
import dfgg.common.NextItemRecommendationNotFoundException;
import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionTag;
import dfgg.domain.item.Item;
import dfgg.presentation.dto.ChampionDto;
import dfgg.presentation.dto.request.NextItemRecommendationRequest;
import dfgg.presentation.dto.response.NextItemRecommendationResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NextItemRecommendationServiceTest {

    private ChampionService championService;
    private ItemService itemService;
    private FallbackChain fallbackChain;
    private NextItemRecommendationService service;

    @BeforeEach
    void setUp() {
        championService = mock(ChampionService.class);
        itemService = mock(ItemService.class);
        fallbackChain = mock(FallbackChain.class);
        service = new NextItemRecommendationService(championService, itemService, fallbackChain);
    }

    private Champion championOf(Long id, String name) {
        Champion champion = mock(Champion.class);
        when(champion.getChampionId()).thenReturn(id);
        when(champion.getName()).thenReturn(name);
        when(champion.getChampionTags()).thenReturn(List.of(ChampionTag.MARKSMAN));
        return champion;
    }

    private NextItemRecommendationRequest requestOf(List<Long> purchasedItemIds) {
        return new NextItemRecommendationRequest(
                new ChampionDto("Jinx", "BOTTOM"),
                purchasedItemIds,
                List.of(
                        new ChampionDto("Ally1", "TOP"), new ChampionDto("Ally1", "JUNGLE"),
                        new ChampionDto("Ally1", "MID"), new ChampionDto("Ally1", "SUPPORT")
                ),
                List.of(
                        new ChampionDto("Enemy1", "TOP"), new ChampionDto("Enemy1", "JUNGLE"),
                        new ChampionDto("Enemy1", "MID"), new ChampionDto("Enemy1", "BOTTOM"),
                        new ChampionDto("Enemy1", "SUPPORT")
                ),
                "PLATINUM",
                "16.16"
        );
    }

    @Test
    @DisplayName("폴백 체인이 추천을 반환하면 아이템 순서를 그대로 유지해 응답으로 변환한다")
    void recommendNextItem_WhenFallbackChainSucceeds_ReturnsItemsInRankedOrder() {
        // given
        Champion myChampion = championOf(222L, "징크스");
        Champion allyChampion = championOf(412L, "럭스");
        Champion enemyChampion = championOf(54L, "말파이트");
        when(championService.findChampionByName("Jinx")).thenReturn(myChampion);
        when(championService.findChampionByName("Ally1")).thenReturn(allyChampion);
        when(championService.findChampionByName("Enemy1")).thenReturn(enemyChampion);

        when(fallbackChain.recommend(any(RecommendationContext.class))).thenReturn(
                Optional.of(new FallbackRecommendation(List.of(3072L, 3006L), FallbackStage.MOST_FREQUENT_BUILD))
        );
        // findItemsByIds는 순서를 보장하지 않으므로 일부러 반대 순서로 반환해 재정렬 로직을 검증한다
        when(itemService.findItemsByIds(List.of(3072L, 3006L))).thenReturn(List.of(
                new Item(3006L, "광전사의 군화"),
                new Item(3072L, "루난의 허리케인")
        ));

        // when
        NextItemRecommendationResponse response = service.recommendNextItem(requestOf(List.of(3031L)));

        // then
        assertThat(response.recommendedItems()).extracting("name")
                .containsExactly("루난의 허리케인", "광전사의 군화");
        assertThat(response.servedBy()).isEqualTo("MOST_FREQUENT_BUILD");
    }

    @Test
    @DisplayName("이미 신발을 샀으면 폴백 체인이 다른 신발을 추천해도 결과에서 제외한다")
    void recommendNextItem_WhenBootsAlreadyPurchased_ExcludesAnotherBootsFromResult() {
        // given: 이미 산 3006(광전사의 군화)도 신발(Boots 태그)이라, 폴백 체인이 추천한
        // 다른 신발 3047(판금 장화)은 빼고 일반 아이템 3072만 남아야 한다.
        Champion myChampion = championOf(222L, "징크스");
        Champion allyChampion = championOf(412L, "럭스");
        Champion enemyChampion = championOf(54L, "말파이트");
        when(championService.findChampionByName("Jinx")).thenReturn(myChampion);
        when(championService.findChampionByName("Ally1")).thenReturn(allyChampion);
        when(championService.findChampionByName("Enemy1")).thenReturn(enemyChampion);
        when(fallbackChain.recommend(any(RecommendationContext.class))).thenReturn(
                Optional.of(new FallbackRecommendation(List.of(3047L, 3072L), FallbackStage.PRIMARY))
        );
        when(itemService.findItemsByIds(List.of(3047L, 3072L))).thenReturn(List.of(
                new Item(3047L, "판금 장화", List.of("Boots")),
                new Item(3072L, "루난의 허리케인", List.of())
        ));
        when(itemService.findItemsByIds(List.of(3006L))).thenReturn(List.of(
                new Item(3006L, "광전사의 군화", List.of("Boots"))
        ));

        // when
        NextItemRecommendationResponse response = service.recommendNextItem(requestOf(List.of(3006L)));

        // then
        assertThat(response.recommendedItems()).extracting("name").containsExactly("루난의 허리케인");
    }

    @Test
    @DisplayName("요청의 챔피언 이름을 정확히 ID로 변환해 폴백 체인에 전달한다")
    void recommendNextItem_WhenCalled_BuildsContextWithResolvedChampionIdsAndPurchasedItems() {
        // given
        Champion myChampion = championOf(222L, "징크스");
        Champion allyChampion = championOf(412L, "럭스");
        Champion enemyChampion = championOf(54L, "말파이트");
        when(championService.findChampionByName("Jinx")).thenReturn(myChampion);
        when(championService.findChampionByName("Ally1")).thenReturn(allyChampion);
        when(championService.findChampionByName("Enemy1")).thenReturn(enemyChampion);
        when(fallbackChain.recommend(any(RecommendationContext.class)))
                .thenReturn(Optional.of(new FallbackRecommendation(List.of(3072L), FallbackStage.PRIMARY)));
        when(itemService.findItemsByIds(List.of(3072L))).thenReturn(List.of(new Item(3072L, "루난의 허리케인")));

        // when
        service.recommendNextItem(requestOf(List.of(3031L)));

        // then
        org.mockito.ArgumentCaptor<RecommendationContext> captor =
                org.mockito.ArgumentCaptor.forClass(RecommendationContext.class);
        org.mockito.Mockito.verify(fallbackChain).recommend(captor.capture());
        RecommendationContext context = captor.getValue();
        assertThat(context.myChampionId()).isEqualTo(222L);
        assertThat(context.purchasedItemIds()).containsExactly(3031L);
        assertThat(context.allyChampionIds()).containsExactly(412L, 412L, 412L, 412L);
        assertThat(context.enemyChampionIds()).containsExactly(54L, 54L, 54L, 54L, 54L);
        assertThat(context.tier()).isEqualTo("PLATINUM");
        assertThat(context.patch()).isEqualTo("16.16");
    }

    @Test
    @DisplayName("폴백 체인이 끝까지 추천을 못 만들면 예외를 던진다")
    void recommendNextItem_WhenFallbackChainReturnsEmpty_ThrowsNextItemRecommendationNotFoundException() {
        // given
        Champion myChampion = championOf(222L, "징크스");
        Champion allyChampion = championOf(412L, "럭스");
        Champion enemyChampion = championOf(54L, "말파이트");
        when(championService.findChampionByName("Jinx")).thenReturn(myChampion);
        when(championService.findChampionByName("Ally1")).thenReturn(allyChampion);
        when(championService.findChampionByName("Enemy1")).thenReturn(enemyChampion);
        when(fallbackChain.recommend(any(RecommendationContext.class))).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.recommendNextItem(requestOf(List.of())))
                .isInstanceOf(NextItemRecommendationNotFoundException.class)
                .hasMessageContaining("징크스")
                .hasMessageContaining("BOTTOM");
    }
}
