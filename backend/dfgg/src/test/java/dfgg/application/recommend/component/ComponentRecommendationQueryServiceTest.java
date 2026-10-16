package dfgg.application.recommend.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.application.recommend.context.CompositionFeatures;
import dfgg.application.recommend.v3.ranker.DecisionTree;
import dfgg.application.recommend.v3.ranker.GradientBoostedTrees;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.match.ItemInventory;
import dfgg.domain.match.ComponentPolicyLookup;
import dfgg.infrastructure.external.dto.ItemResponse;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ComponentRecommendationQueryServiceTest {
    private static final CompositionFeatures COMPOSITION = new CompositionFeatures(1, 2, 1, 0, 1, 1, 1, 1, 1);
    private final ItemResponse response;
    private final GradientBoostedTrees model = new GradientBoostedTrees(List.of(new DecisionTree(
            new int[]{0}, new double[]{150}, new boolean[]{true}, new int[]{-1}, new int[]{-2},
            new double[]{1, 3}, new double[]{2}, new double[]{1, 1})));

    ComponentRecommendationQueryServiceTest() throws Exception {
        response = new ObjectMapper().readValue("""
                {"version":"16.18.1","data":{
                  "1":{"gold":{"total":100,"purchasable":true}},
                  "2":{"gold":{"total":200,"purchasable":true}},
                  "3":{"from":["1","1","2"],"gold":{"total":1000,"purchasable":true}},
                  "4":{"from":["1","2"],"gold":{"total":1500,"purchasable":true}},
                  "5":{"gold":{"total":150,"purchasable":true}},
                  "3070":{"gold":{"total":150,"purchasable":true}}
                }}
                """, ItemResponse.class);
    }

    @ParameterizedTest
    @MethodSource("positions")
    void query_and_legacy_calls_produce_identical_complete_results(String legacyPosition, ChampionPosition position) {
        var service = new ComponentRecommendationService(model);
        var targets = List.of(4, 3, 999);
        var inventory = ItemInventory.empty().add(1);
        var query = query(81, targets, inventory, position, "16.18");

        var results = service.recommend(query, response);
        var legacy = service.recommend(81, targets, inventory, 200, 600_000,
                legacyPosition, "16.18", response, COMPOSITION);

        assertThat(results).isEqualTo(legacy);
        assertThat(results).extracting(r -> r.targetItemId()).containsExactly(4, 3, 999);
        assertThat(results.getLast().status()).isEqualTo(ComponentRecommendationService.Status.UNSUPPORTED_CATALOG_ITEM);
        assertThat(results.getFirst().rankedPurchases()).isNotEmpty().allSatisfy(p -> {
            assertThat(p.additionalCost()).isLessThanOrEqualTo(200);
            assertThat(p.candidate().features().get(22)).isEqualTo(10);
            assertThat(p.candidate().features().subList(46, 55)).isEqualTo(COMPOSITION.values());
            assertThat(p.candidate().features().get(28)).isEqualTo(position == null ? 1.0 : 0.0);
        });
        assertThat(inventory.quantities()).isEqualTo(Map.of(1, 1));
    }

    @Test
    void query_and_legacy_calls_preserve_policy_features_and_lookup_validation() {
        var lookup = new ComponentPolicyLookup("16.18", "a".repeat(64), Map.of(
                new ComponentPolicyLookup.RoleKey(81, "TOP"), new ComponentPolicyLookup.Prepared(1062, .85)));
        var service = new ComponentRecommendationService(model, lookup);
        var inventory = ItemInventory.empty();
        var query = query(81, List.of(3), inventory, ChampionPosition.TOP, "16.18");
        var results = service.recommend(query, response);

        assertThat(results).isEqualTo(service.recommend(81, List.of(3), inventory, 200, 600_000,
                "TOP", "16.18", response, COMPOSITION));
        assertThat(results.getFirst().rankedPurchases()).anySatisfy(p -> {
            assertThat(p.candidate().itemQuantities()).isEqualTo(Map.of(3070, 1));
            assertThat(p.candidate().features().subList(55, 58)).containsExactly(1.0, .85, Math.log1p(1062));
        });
        assertThatThrownBy(() -> service.recommend(query(null, List.of(3), inventory, ChampionPosition.TOP, "16.18"), response))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Champion ID");
        assertThatThrownBy(() -> service.recommend(query(81, List.of(3), inventory, ChampionPosition.TOP, "16.19"), response))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("조회표 패치");
    }

    @Test
    void championless_evaluation_call_remains_available_without_preparation() {
        var service = new ComponentRecommendationService(model);
        var query = query(null, List.of(3), ItemInventory.empty(), ChampionPosition.TOP, "16.18");
        assertThat(service.recommend(query, response)).isEqualTo(service.recommend(List.of(3),
                ItemInventory.empty(), 200, 600_000, "TOP", "16.18", response, COMPOSITION));
    }

    @Test
    void catalog_must_match_query_patch_even_without_lookup() {
        var service = new ComponentRecommendationService(model);
        assertThatThrownBy(() -> service.recommend(query(81, List.of(3), ItemInventory.empty(),
                ChampionPosition.TOP, "16.19"), response))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("카탈로그의 패치");
    }

    private ComponentRecommendationQuery query(Integer championId, List<Integer> targets,
            ItemInventory inventory, ChampionPosition position, String patch) {
        return new ComponentRecommendationQuery(championId, targets, inventory, 200, 600_000, position, patch, COMPOSITION);
    }

    private static Stream<Arguments> positions() {
        return Stream.of(Arguments.of("TOP", ChampionPosition.TOP), Arguments.of("MIDDLE", ChampionPosition.MID),
                Arguments.of("UTILITY", ChampionPosition.SUPPORT), Arguments.of(" bottom ", ChampionPosition.BOTTOM),
                Arguments.of("UNKNOWN", null), Arguments.of("", null), Arguments.of(null, null));
    }
}
