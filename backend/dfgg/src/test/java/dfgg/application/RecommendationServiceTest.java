package dfgg.application;

import dfgg.application.champion.ChampionService;
import dfgg.common.CompositionStatsNotFoundException;
import dfgg.domain.stats.ChampionBuildStatsRepository;
import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.champion.ChampionTag;
import dfgg.domain.item.Item;
import dfgg.domain.stats.ChampionBuildStats;
import dfgg.presentation.dto.ChampionDto;
import dfgg.presentation.dto.request.RecommendationRequest;
import dfgg.presentation.dto.response.RecommendationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    private RecommendationService recommendationService;

    @Mock
    private ChampionService championService;

    @Mock
    private ChampionBuildStatsRepository statsRepository;

    private final RecommendationBuildComposer buildComposer = new RecommendationBuildComposer();

    @BeforeEach
    void setUp() {
        recommendationService = new RecommendationService(championService, statsRepository, buildComposer);
    }

    @Test
    @DisplayName("여러 buildKey의 통계를 슬롯별로 병합해 하나의 buildKey보다 많은 아이템을 추천한다")
    void recommend_success() {
        // given
        RecommendationRequest request = new RecommendationRequest(
                new ChampionDto("징크스", "BOTTOM"),
                List.of(new ChampionDto("쓰레쉬", "SUPPORT")),
                List.of(new ChampionDto("케이틀린", "BOTTOM"))
        );

        Champion myChampion = mock(Champion.class);
        when(myChampion.getChampionId()).thenReturn(1L);
        when(myChampion.getName()).thenReturn("징크스");

        Champion thresh = mock(Champion.class);
        when(thresh.getChampionTags()).thenReturn(List.of(ChampionTag.SUPPORT));

        Champion caitlyn = mock(Champion.class);
        when(caitlyn.getChampionTags()).thenReturn(List.of(ChampionTag.MARKSMAN));

        when(championService.findChampionByName("징크스")).thenReturn(myChampion);
        when(championService.findChampionByName("쓰레쉬")).thenReturn(thresh);
        when(championService.findChampionByName("케이틀린")).thenReturn(caitlyn);

        Item item1 = new Item(1L, "아이템1");
        Item item2 = new Item(2L, "아이템2");
        Item item3 = new Item(3L, "아이템3");

        ChampionBuildStats shortPopularStats = new ChampionBuildStats(
                "16.15", 420, myChampion, ChampionPosition.BOTTOM,
                null, null, null, null, null,
                "PLATINUM", "SHORT", List.of(item1, item2), 30, 50
        );
        ChampionBuildStats longRareStats = new ChampionBuildStats(
                "16.15", 420, myChampion, ChampionPosition.BOTTOM,
                null, null, null, null, null,
                "PLATINUM", "LONG", List.of(item1, item2, item3), 2, 3
        );

        when(statsRepository.findAllMatchingStats(
                eq(1L), eq("BOTTOM"),
                anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean()
        )).thenReturn(List.of(shortPopularStats, longRareStats));

        // when
        RecommendationResponse response = recommendationService.recommend(request);

        // then
        assertThat(response.champion()).isEqualTo("징크스");
        assertThat(response.position()).isEqualTo("BOTTOM");
        assertThat(response.items()).hasSize(3);
    }

    @Test
    @DisplayName("매칭되는 통계가 없으면 예외가 발생한다")
    void recommend_WhenStatsNotFound_ThrowCompositionStatsNotFoundException() {
        // given
        RecommendationRequest request = new RecommendationRequest(
                new ChampionDto("징크스", "BOTTOM"),
                List.of(),
                List.of()
        );

        Champion myChampion = mock(Champion.class);
        when(myChampion.getChampionId()).thenReturn(1L);
        when(myChampion.getName()).thenReturn("징크스");

        when(championService.findChampionByName(anyString())).thenReturn(myChampion);

        when(statsRepository.findAllMatchingStats(
                anyLong(), anyString(),
                anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean()
        )).thenReturn(List.of());

        // when & then
        assertThatThrownBy(() -> recommendationService.recommend(request))
                .isInstanceOf(CompositionStatsNotFoundException.class)
                .hasMessageContaining("징크스")
                .hasMessageContaining("BOTTOM");
    }
}
