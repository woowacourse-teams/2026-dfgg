package dfgg.application.recommend.component;

import static dfgg.application.recommend.context.TestCompositions.NO_TAGS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.domain.match.BuildPathResolver;
import dfgg.domain.match.ItemInventory;
import dfgg.infrastructure.external.catalog.DataDragonPurchaseItemCatalog;
import dfgg.infrastructure.external.dto.ItemResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ComponentTargetRelationshipTest {
    private final ItemResponse response;
    private final DataDragonPurchaseItemCatalog catalog;
    private final BuildPathResolver resolver;

    ComponentTargetRelationshipTest() throws Exception {
        response = new ObjectMapper().readValue("""
                {"version":"16.18.1","data":{
                 "1":{"gold":{"total":350,"purchasable":true}},
                 "2":{"gold":{"total":875,"purchasable":true}},
                 "3":{"from":["1","1"],"gold":{"total":1000,"purchasable":true}},
                 "4":{"from":["3","2"],"gold":{"total":3000,"purchasable":true}},
                 "5":{"from":["1","2"],"gold":{"total":1500,"purchasable":true}},
                 "1001":{"tags":["Boots"],"gold":{"total":300,"purchasable":true}},
                 "1029":{"gold":{"total":300,"purchasable":true}},
                 "3047":{"tags":["Boots"],"from":["1001","1029"],"gold":{"total":1200,"purchasable":true}},
                 "3867":{"inStore":false,"gold":{"total":400,"purchasable":false}},
                 "3869":{"from":["3867"],"gold":{"total":400,"purchasable":true}}
                }}
                """, ItemResponse.class);
        catalog = new DataDragonPurchaseItemCatalog(response.data());
        resolver = new BuildPathResolver(catalog);
    }

    @Test
    void same_candidate_has_same_41_features_but_different_target_relationships() {
        var before = ItemInventory.empty();
        var candidate = find(4, before, 350, Map.of(1, 1));
        var a = vector(before, candidate, 4);
        var b = vector(before, candidate, 5);
        assertThat(a).hasSize(59);
        assertThat(a.subList(0, 41)).isEqualTo(b.subList(0, 41));
        assertThat(a.subList(41, 46)).isNotEqualTo(b.subList(41, 46));
        assertThat(a.get(41)).isEqualTo(2650);
        assertThat(b.get(41)).isEqualTo(1150);
        assertThat(a.get(43)).isZero(); // 간접 재료
        assertThat(b.get(43)).isEqualTo(1); // 직접 재료
        assertThat(a.get(44)).isCloseTo(1.0 / 3, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(b.get(44)).isEqualTo(0.5);
        assertThat(a.get(45)).isCloseTo(350.0 / 3000, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(b.get(45)).isCloseTo(350.0 / 1500, org.assertj.core.data.Offset.offset(1e-12));
    }

    @Test
    void actual_patch_long_sword_differs_between_black_cleaver_and_deaths_dance() throws Exception {
        ItemResponse patch;
        try (var input = getClass().getResourceAsStream("/item/component-target-recipes-16.18.1.json")) {
            patch = new ObjectMapper().readValue(input, ItemResponse.class);
        }
        var patchCatalog = new DataDragonPurchaseItemCatalog(patch.data());
        var paths = new BuildPathResolver(patchCatalog);
        var before = ItemInventory.empty();
        var vectors = new java.util.ArrayList<List<Double>>();
        for (int target : List.of(3071, 6333)) {
            var sword = paths.resolve(target, before, 350).stream().filter(c -> c.itemQuantities().equals(Map.of(1036, 1))).findFirst().orElseThrow();
            vectors.add(ComponentCandidateFeatures.calculate(before, sword, target, patchCatalog, patch, 600_000, "TOP", NO_TAGS).features());
        }
        assertThat(vectors.get(0).subList(0, 41)).isEqualTo(vectors.get(1).subList(0, 41));
        assertThat(vectors.get(0).subList(41, 46)).isNotEqualTo(vectors.get(1).subList(41, 46));
        assertThat(vectors.get(0).get(41)).isEqualTo(2650);
        assertThat(vectors.get(1).get(41)).isEqualTo(2950);
        assertThat(vectors.get(0).get(44)).isCloseTo(1.0 / 5, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(vectors.get(1).get(44)).isCloseTo(1.0 / 7, org.assertj.core.data.Offset.offset(1e-12));
    }

    @Test
    void direct_material_is_new_purchase_not_owned_material_consumed_by_completion() {
        var before = ItemInventory.empty().add(3).add(2);
        var complete = find(4, before, 3000, Map.of(4, 1));
        var features = vector(before, complete, 4);
        assertThat(features.get(43)).isZero();
        assertThat(features.subList(41, 45)).containsExactly(0.0, 0.0, 0.0, 1.0);
        var direct = vector(ItemInventory.empty(), find(4, ItemInventory.empty(), 2000, Map.of(3, 1, 2, 1)), 4);
        assertThat(direct.get(43)).isEqualTo(1);
        assertThat(direct.get(44)).isEqualTo(1);
        assertThat(direct.get(41)).isEqualTo(1125);
    }

    @Test
    void intermediate_purchase_consumes_owned_leaf_without_counting_it_twice() {
        var before = ItemInventory.empty().add(1);
        var candidate = find(4, before, 650, Map.of(3, 1));
        assertThat(candidate.inventoryAfter().quantities()).isEqualTo(Map.of(3, 1));
        var features = vector(before, candidate, 4);
        assertThat(features.get(41)).isEqualTo(2000);
        assertThat(features.get(43)).isEqualTo(1);
        assertThat(features.get(44)).isCloseTo(2.0 / 3, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(features.get(45)).isCloseTo(650.0 / 3000, org.assertj.core.data.Offset.offset(1e-12));
    }

    @Test
    void tier_two_boots_consumes_basic_boots_with_existing_budget_and_slot_rules() {
        var before = ItemInventory.empty().add(1001);
        assertThat(resolver.resolve(3047, before, 899)).extracting(BuildPathResolver.PurchaseBundle::itemQuantities).doesNotContain(Map.of(3047, 1));
        var candidate = find(3047, before, 900, Map.of(3047, 1));
        assertThat(candidate.additionalCost()).isEqualTo(900);
        assertThat(candidate.inventoryAfter().quantities()).isEqualTo(Map.of(3047, 1));
        assertThat(vector(before, candidate, 3047).subList(41, 45)).containsExactly(0.0, 0.0, 0.0, 1.0);
        var basic = find(3047, ItemInventory.empty(), 300, Map.of(1001, 1));
        assertThat(vector(ItemInventory.empty(), basic, 3047).get(41)).isEqualTo(900);
    }

    @Test
    void support_final_requires_observed_quest_precursor_and_never_synthesizes_purchase() {
        var before = ItemInventory.empty().add(3867);
        var candidate = find(3869, before, 0, Map.of(3869, 1));
        assertThat(candidate.additionalCost()).isZero();
        assertThat(vector(before, candidate, 3869).subList(41, 46)).containsExactly(0.0, 0.0, 0.0, 1.0, 0.0);
        assertThatThrownBy(() -> vector(ItemInventory.empty(),
                new BuildPathResolver.PurchaseBundle(Map.of(3869, 1), 400, ItemInventory.empty().add(3869), List.of(3869)), 3869))
                .hasMessageContaining("UNSUPPORTED_TARGET_MECHANIC");
    }

    @Test
    void core_top_five_keep_common_candidates_and_failures_in_their_own_branches() {
        var tree = new dfgg.application.recommend.v3.ranker.DecisionTree(new int[0], new double[0], new boolean[0],
                new int[0], new int[0], new double[]{1.0}, new double[0], new double[]{1.0});
        var service = new ComponentRecommendationService(new dfgg.application.recommend.v3.ranker.GradientBoostedTrees(List.of(tree)));
        var results = service.recommend(List.of(4, 5, 3047, 3869, 3097), ItemInventory.empty(), 350, 600_000, "TOP", "16.18", response, NO_TAGS);
        assertThat(results).extracting(ComponentRecommendationService.TargetRecommendation::targetItemId).containsExactly(4, 5, 3047, 3869, 3097);
        assertThat(results.get(0).rankedPurchases().getFirst().candidate().itemQuantities()).isEqualTo(Map.of(1, 1));
        assertThat(results.get(1).rankedPurchases().getFirst().candidate().itemQuantities()).isEqualTo(Map.of(1, 1));
        assertThat(results.subList(0, 3)).allMatch(r -> r.status() == ComponentRecommendationService.Status.OK);
        assertThat(results.get(3).status()).isEqualTo(ComponentRecommendationService.Status.UNSUPPORTED_TARGET_MECHANIC);
        assertThat(results.get(4).status()).isEqualTo(ComponentRecommendationService.Status.UNSUPPORTED_CATALOG_ITEM);
        assertThat(results.get(0).rankedPurchases().getFirst().rankWithinTarget()).isEqualTo(1);
        assertThat(results.get(1).rankedPurchases().getFirst().rankWithinTarget()).isEqualTo(1);
        assertThatThrownBy(() -> service.recommend(List.of(4), ItemInventory.empty(), 350, 0, "TOP", "16.19", response, NO_TAGS))
                .hasMessageContaining("패치");
    }

    private BuildPathResolver.PurchaseBundle find(int target, ItemInventory before, long budget, Map<Integer, Integer> items) {
        return resolver.resolve(target, before, budget).stream().filter(c -> c.itemQuantities().equals(items)).findFirst().orElseThrow();
    }

    private List<Double> vector(ItemInventory before, BuildPathResolver.PurchaseBundle candidate, int target) {
        return ComponentCandidateFeatures.calculate(before, candidate, target, catalog, response, 600_000, "TOP", NO_TAGS).features();
    }
}
