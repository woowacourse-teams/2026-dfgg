package dfgg.application.recommend.component;

import static dfgg.application.recommend.context.TestCompositions.NO_TAGS;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.application.recommend.v3.ranker.DecisionTree;
import dfgg.application.recommend.v3.ranker.GradientBoostedTrees;
import dfgg.domain.match.ComponentCandidateGenerator;
import dfgg.domain.match.ItemInventory;
import dfgg.domain.match.PreparationLookup;
import dfgg.infrastructure.external.dto.ItemResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 랭커 분리 전후에 같은 후보·점수·동점 순서·오류 처리를 유지하는지 검증한다. */
class ComponentRecommendationRankingTest {
    private final ItemResponse response;

    ComponentRecommendationRankingTest() throws Exception {
        response = new ObjectMapper().readValue("""
                {"version":"16.18.1","data":{
                  "1":{"gold":{"total":100,"purchasable":true}},
                  "2":{"gold":{"total":200,"purchasable":true}},
                  "3":{"from":["1","1","2"],"gold":{"total":1000,"purchasable":true}},
                  "4":{"gold":{"total":150,"purchasable":true}},
                  "5":{"from":["1"],"gold":{"total":500,"purchasable":true}}
                }}
                """, ItemResponse.class);
    }

    @Test
    void sorts_by_model_score_and_keeps_generation_order_for_ties() {
        var service = new ComponentRecommendationService(splitModel(0, 150, -3, -1));
        var result = recommend(service, List.of(3), 200).getFirst();

        assertThat(result.status()).isEqualTo(ComponentRecommendationService.Status.OK);
        assertThat(result.rankedPurchases()).extracting(p -> p.candidate().itemQuantities())
                .containsExactly(Map.of(2, 1), Map.of(1, 2), Map.of(1, 1));
        assertThat(result.rankedPurchases()).extracting(p -> p.score()).containsExactly(-1.0, -1.0, -3.0);
        assertThat(result.rankedPurchases()).extracting(p -> p.rankWithinTarget()).containsExactly(1, 2, 3);
        assertThat(result.rankedPurchases()).extracting(p -> p.additionalCost()).containsExactly(200L, 200L, 100L);
        assertThat(result.rankedPurchases().get(1).candidate().purchaseOrder()).containsExactly(1, 1);
        assertThat(result.rankedPurchases().get(1).candidate().inventoryAfter()).isEqualTo(Map.of(1, 2));
        assertThat(result.rankedPurchases()).allSatisfy(p -> {
            assertThat(p.candidate().features()).hasSize(59);
            assertThat(p.candidate().features().get(22)).isEqualTo(10.0);
            assertThat(p.candidateSource()).isEqualTo(ComponentCandidateGenerator.Source.TARGET_PATH);
            assertThat(p.preparationPatternCount()).isNull();
            assertThat(p.preparationFutureRecipeLinkRate()).isNull();
        });
    }

    @Test
    void ranks_preparation_features_and_preserves_their_evidence() {
        var pattern = new PreparationLookup.Pattern(4, 50, .8, 200, 40);
        var lookup = new PreparationLookup("16.18", "a".repeat(64),
                Map.of(new PreparationLookup.Key(81, "BOTTOM", 3), List.of(pattern)));
        var service = new ComponentRecommendationService(splitModel(55, .5, 1, 5), lookup);
        var ranked = recommend(service, List.of(3), 200).getFirst().rankedPurchases();

        assertThat(ranked).hasSize(4);
        var preparation = ranked.getFirst();
        assertThat(preparation.candidate().itemQuantities()).isEqualTo(Map.of(4, 1));
        assertThat(preparation.additionalCost()).isEqualTo(150);
        assertThat(preparation.score()).isEqualTo(5);
        assertThat(preparation.rankWithinTarget()).isEqualTo(1);
        assertThat(preparation.candidateSource()).isEqualTo(ComponentCandidateGenerator.Source.PREPARATION);
        assertThat(preparation.preparationPatternCount()).isEqualTo(50);
        assertThat(preparation.preparationFutureRecipeLinkRate()).isEqualTo(.8);
        assertThat(preparation.candidate().features().subList(55, 59))
                .containsExactly(1.0, .25, Math.log1p(50), .8);
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void nonfinite_score_fails_only_its_target_and_discards_partial_ranking(double invalidScore) {
        // 목표 3은 비싼 후보에서 실패하고, 목표 5의 100골드 후보는 정상적으로 점수를 얻는다.
        var service = new ComponentRecommendationService(splitModel(0, 150, 2, invalidScore));
        var results = recommend(service, List.of(3, 5), 200);

        assertThat(results).extracting(r -> r.targetItemId()).containsExactly(3, 5);
        assertThat(results.getFirst().status()).isEqualTo(ComponentRecommendationService.Status.CALCULATION_FAILED);
        assertThat(results.getFirst().reason()).isEqualTo("모델 점수가 유한하지 않습니다.");
        assertThat(results.getFirst().rankedPurchases()).isEmpty();
        assertThat(results.getLast().status()).isEqualTo(ComponentRecommendationService.Status.OK);
        assertThat(results.getLast().rankedPurchases()).singleElement().satisfies(p -> {
            assertThat(p.score()).isEqualTo(2);
            assertThat(p.rankWithinTarget()).isEqualTo(1);
        });
    }

    @Test
    void no_candidate_does_not_attempt_to_score() {
        var service = new ComponentRecommendationService(splitModel(100, 0, 1, 2));
        var result = recommend(service, List.of(3), 0).getFirst();
        assertThat(result.status()).isEqualTo(ComponentRecommendationService.Status.NO_CANDIDATE);
        assertThat(result.rankedPurchases()).isEmpty();
    }

    private List<ComponentRecommendationService.TargetRecommendation> recommend(
            ComponentRecommendationService service, List<Integer> targets, long gold) {
        return service.recommend(81, targets, ItemInventory.empty(), gold, 600_000,
                "BOTTOM", "16.18", response, NO_TAGS);
    }

    private GradientBoostedTrees splitModel(int feature, double threshold, double leftScore, double rightScore) {
        var tree = new DecisionTree(new int[]{feature}, new double[]{threshold}, new boolean[]{true},
                new int[]{-1}, new int[]{-2}, new double[]{leftScore, rightScore},
                new double[]{2}, new double[]{1, 1});
        return new GradientBoostedTrees(List.of(tree));
    }
}
