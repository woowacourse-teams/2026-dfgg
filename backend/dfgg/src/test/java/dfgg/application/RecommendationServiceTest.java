package dfgg.application;

import dfgg.common.CompositionStatsNotFoundException;
import dfgg.domain.ChampionBuildStatsRepository;
import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionTag;
import dfgg.domain.item.Item;
import dfgg.domain.stats.ChampionBuildStats;
import dfgg.presentation.dto.ChampionInfo;
import dfgg.presentation.dto.request.RecommendationRequest;
import dfgg.presentation.dto.response.RecommendationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    private RecommendationService recommendationService;

    @Mock
    private ChampionNameNormalizer championNameNormalizer;

    @Mock
    private ChampionBuildStatsRepository statsRepository;

    @BeforeEach
    void setUp() {
        recommendationService = new RecommendationService(championNameNormalizer, statsRepository);
    }

    @Test
    @DisplayName("아이템 빌드를 정상 추천한다")
    void recommend_success() {
        // given
        RecommendationRequest request = new RecommendationRequest(
                new ChampionInfo("징크스", "BOTTOM"),
                List.of(new ChampionInfo("쓰레쉬", "SUPPORT")),
                List.of(new ChampionInfo("케이틀린", "BOTTOM"))
        );

        Champion myChampion = mock(Champion.class);
        when(myChampion.getChampionId()).thenReturn(1L);
        when(myChampion.getName()).thenReturn("징크스");

        Champion thresh = mock(Champion.class);
        when(thresh.getChampionTags()).thenReturn(List.of(ChampionTag.SUPPORT));

        Champion caitlyn = mock(Champion.class);
        when(caitlyn.getChampionTags()).thenReturn(List.of(ChampionTag.MARKSMAN));

        when(championNameNormalizer.normalize("징크스")).thenReturn(myChampion);
        when(championNameNormalizer.normalize("쓰레쉬")).thenReturn(thresh);
        when(championNameNormalizer.normalize("케이틀린")).thenReturn(caitlyn);

        Item item1 = mock(Item.class);
        Item item2 = mock(Item.class);

        ChampionBuildStats bestStats = mock(ChampionBuildStats.class);
        when(bestStats.getItems()).thenReturn(List.of(item1, item2));

        when(statsRepository.findBestMatchingStats(
                eq(1L), eq("BOTTOM"),
                anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean()
        )).thenReturn(Optional.of(bestStats));

        // when
        RecommendationResponse response = recommendationService.recommend(request);

        // then
        assertThat(response.champion()).isEqualTo("징크스");
        assertThat(response.position()).isEqualTo("BOTTOM");
        assertThat(response.items()).hasSize(2);
    }

    @Test
    @DisplayName("매칭되는 통계가 없으면 예외가 발생한다")
    void recommend_WhenStatsNotFound_ThrowCompositionStatsNotFoundException() {
        // given
        RecommendationRequest request = new RecommendationRequest(
                new ChampionInfo("징크스", "BOTTOM"),
                List.of(),
                List.of()
        );

        Champion myChampion = mock(Champion.class);
        when(myChampion.getChampionId()).thenReturn(1L);
        when(myChampion.getName()).thenReturn("징크스");

        when(championNameNormalizer.normalize(anyString())).thenReturn(myChampion);

        when(statsRepository.findBestMatchingStats(
                anyLong(), anyString(),
                anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean()
        )).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> recommendationService.recommend(request))
                .isInstanceOf(CompositionStatsNotFoundException.class)
                .hasMessageContaining("징크스")
                .hasMessageContaining("BOTTOM");
    }
}
