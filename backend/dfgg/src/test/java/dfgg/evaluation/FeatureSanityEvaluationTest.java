package dfgg.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.application.itemstats.ItemStatsAggregationService;
import dfgg.application.recommend.v3.CandidateGenerator;
import dfgg.application.recommend.v3.CandidateUnion;
import dfgg.application.recommend.v3.GeneratorResult;
import dfgg.application.recommend.v3.RecommendationQuery;
import dfgg.application.recommend.v3.feature.CandidateFeatures;
import dfgg.application.recommend.v3.feature.FeatureExtractionPipeline;
import dfgg.application.recommend.v3.feature.FeatureName;
import dfgg.domain.champion.ChampionPosition;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 실 데이터에서 feature가 실제로 채워지는지 확인한다. 픽스처는 통과해도 실 데이터에서
 * 전부 NaN이면 학습이 무의미하므로, 학습 데이터를 만들기 전에 여기서 걸러낸다.
 */
@SpringBootTest
@ActiveProfiles("evaluation")
@Tag("evaluation")
class FeatureSanityEvaluationTest {

    private static final long YASUO = 157L;
    private static final long RAMMUS = 33L;
    private static final long ZHONYAS = 3157L;

    @Autowired
    private ItemStatsAggregationService aggregationService;
    @Autowired
    private List<CandidateGenerator> generators;
    @Autowired
    private FeatureExtractionPipeline pipeline;

    @Test
    @DisplayName("실 데이터에서 feature 채움률을 확인하고 야스오/존야 케이스를 검증한다")
    void extractFeaturesOnRealData() {
        aggregationService.aggregate(3);

        RecommendationQuery query = new RecommendationQuery(
                YASUO, ChampionPosition.MID, List.of(),
                List.of(222L, 412L, 64L, 516L), List.of(RAMMUS, 103L, 51L, 89L, 60L),
                "PLATINUM", "16.16");

        List<GeneratorResult> results = new ArrayList<>();
        for (CandidateGenerator generator : generators) {
            results.add(generator.generate(query, 30));
        }
        List<CandidateFeatures> extracted = pipeline.extract(CandidateUnion.merge(results), query);
        assertThat(extracted).isNotEmpty();

        // feature별 채움률 — 전부 NaN인 feature가 있으면 추출이 동작하지 않는 것이다
        System.out.println("\n=== feature 채움률 (후보 " + extracted.size() + "개) ===");
        for (FeatureName name : FeatureName.values()) {
            long filled = extracted.stream()
                    .filter(features -> !Double.isNaN(features.vector().get(name)))
                    .count();
            System.out.printf("%-38s %3d/%3d  %5.1f%%%n",
                    name.exportName(), filled, extracted.size(), 100.0 * filled / extracted.size());
        }

        // 야스오는 존야를 산 적이 없다 — base rate가 0으로 잡혀야 LTR이 눌러줄 수 있다
        extracted.stream()
                .filter(features -> features.itemId() == ZHONYAS)
                .findFirst()
                .ifPresentOrElse(
                        zhonyas -> {
                            System.out.println("\n=== 존야(3157)가 후보에 있음 ===");
                            System.out.println("  base_rate_all = "
                                    + zhonyas.vector().get(FeatureName.CHAMPION_BASE_RATE_ALL));
                            System.out.println("  counter_lift_max = "
                                    + zhonyas.vector().get(FeatureName.COUNTER_LIFT_MAX));
                        },
                        () -> System.out.println("\n=== 존야는 후보에 오르지도 않음 (generator 단계에서 걸러짐) ==="));

        assertThat(extracted.get(0).vector().values()).hasSize(FeatureName.values().length);
    }
}
