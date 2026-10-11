package dfgg.application.recommend.component;

import static dfgg.application.recommend.context.TestCompositions.NO_TAGS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.application.recommend.v3.ranker.GradientBoostedTrees;
import dfgg.domain.match.ComponentCandidateGenerator;
import dfgg.domain.match.ItemInventory;
import dfgg.domain.match.PreparationLookup;
import dfgg.infrastructure.external.catalog.DataDragonPurchaseItemCatalog;
import dfgg.infrastructure.external.dto.ItemResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ComponentRankerTest {
    private final ItemResponse response;
    private final DataDragonPurchaseItemCatalog catalog;
    private final GradientBoostedTrees model = mock(GradientBoostedTrees.class);
    private final ComponentRanker ranker = new ComponentRanker(model);

    ComponentRankerTest() throws Exception {
        response = new ObjectMapper().readValue("""
                {"version":"16.18.1","data":{
                  "1":{"gold":{"total":100,"purchasable":true}},
                  "2":{"gold":{"total":200,"purchasable":true}},
                  "3":{"from":["1","2"],"gold":{"total":1000,"purchasable":true}}
                }}
                """, ItemResponse.class);
        catalog = new DataDragonPurchaseItemCatalog(response.data());
    }

    @Test
    void ranking_does_not_reorder_input_or_accumulate_state_across_calls() {
        when(model.predict(any(double[].class))).thenAnswer(invocation -> ((double[]) invocation.getArgument(0))[0]);
        var candidates = new ArrayList<>(new ComponentCandidateGenerator(catalog, PreparationLookup.disabled())
                .generate(81, "BOTTOM", "16.18", 3, ItemInventory.empty(), 200));
        var original = List.copyOf(candidates);

        var ranked = rank(candidates);

        assertThat(ranked).extracting(p -> p.candidate().itemQuantities()).containsExactly(Map.of(2, 1), Map.of(1, 1));
        assertThat(ranked).extracting(ScoredPurchase::score).containsExactly(200.0, 100.0);
        assertThat(ranked).extracting(ScoredPurchase::rankWithinTarget).containsExactly(1, 2);
        assertThat(candidates).containsExactlyElementsOf(original);
        assertThat(rank(candidates)).isEqualTo(ranked);
        assertThatThrownBy(ranked::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void empty_candidates_do_not_call_model() {
        assertThat(rank(List.of())).isEmpty();
        verifyNoInteractions(model);
    }

    private List<ScoredPurchase> rank(List<ComponentCandidateGenerator.Candidate> candidates) {
        return ranker.rank(candidates, ItemInventory.empty(), 3, catalog, response, 600_000, "BOTTOM", NO_TAGS);
    }
}
