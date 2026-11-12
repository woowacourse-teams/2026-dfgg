package dfgg.application.recommend.component;

import static dfgg.application.recommend.context.TestCompositions.NO_TAGS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import dfgg.application.recommend.NextItemRecommendationService;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.language.Language;
import dfgg.domain.match.ItemInventory;
import dfgg.infrastructure.external.dto.ItemResponse;
import dfgg.presentation.dto.ChampionDto;
import dfgg.presentation.dto.RecommendedItemDto;
import dfgg.presentation.dto.request.NextItemRecommendationRequest;
import dfgg.presentation.dto.response.NextItemRecommendationResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CoreComponentRecommendationServiceTest {
    @Test
    void existing_core_top_five_are_connected_in_order_without_replacing_failed_targets() {
        var core = mock(NextItemRecommendationService.class);
        var champions = mock(dfgg.application.champion.ChampionService.class);
        var champion = mock(dfgg.domain.champion.Champion.class);
        when(champion.getChampionId()).thenReturn(86L);
        when(champions.findChampionByName("Garen")).thenReturn(champion);
        var compositions = mock(dfgg.application.recommend.context.CompositionFeatureCalculator.class);
        when(compositions.calculate(anyList(), anyList())).thenReturn(NO_TAGS);
        var component = mock(ComponentRecommendationService.class);
        var request = new NextItemRecommendationRequest(new ChampionDto("Garen", "TOP"), List.of(), List.of(), List.of(), "EMERALD", "16.18");
        var catalog = new ItemResponse("16.18.1", null, Map.of());
        var ids = List.of(3071, 6333, 3047, 3869, 3097);
        var coreItems = ids.stream().map(id -> new RecommendedItemDto(id.longValue(), "test", "image", null)).toList();
        var branches = ids.stream().map(id -> new ComponentRecommendationService.TargetRecommendation(id,
                id == 3097 ? ComponentRecommendationService.Status.UNSUPPORTED_CATALOG_ITEM : ComponentRecommendationService.Status.OK,
                List.of(), id == 3097 ? "missing" : null)).toList();
        when(core.recommendNextItem(request, Language.KO_KR)).thenReturn(new NextItemRecommendationResponse(coreItems));
        var expectedQuery = new ComponentRecommendationQuery(86, ids, ItemInventory.empty(), 350, 600_000,
                ChampionPosition.TOP, "16.18", NO_TAGS);
        when(component.recommend(expectedQuery, catalog)).thenReturn(branches);
        var results = new CoreComponentRecommendationService(core, component, champions, compositions).recommend(request, ItemInventory.empty(), 350, 600_000, catalog);
        assertThat(results).extracting(r -> r.target().id()).containsExactly(3071L, 6333L, 3047L, 3869L, 3097L);
        assertThat(results).extracting(CoreComponentRecommendationService.TargetRecommendation::coreRank).containsExactly(1, 2, 3, 4, 5);
        assertThat(results.getLast().component().status()).isEqualTo(ComponentRecommendationService.Status.UNSUPPORTED_CATALOG_ITEM);
        verify(core, times(1)).recommendNextItem(request, Language.KO_KR);
        verify(component, times(1)).recommend(expectedQuery, catalog);
    }
}
