package dfgg.application.recommend.v3.feature;

import dfgg.application.itemstats.ItemStatsAggregationService;
import dfgg.application.recommend.v3.*;

import dfgg.domain.champion.ChampionPosition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ItemStatsAggregationService.class, FeatureExtractionPipeline.class,
        CandidateFeatureExtractor.class, StatsFeatureExtractor.class, QueryFeatureExtractor.class,
        dfgg.application.recommend.v3.generator.PairSynergyRetriever.class,
        dfgg.application.recommend.v3.generator.CounterLiftCalculator.class,
        dfgg.application.utils.WilsonScoreCalculator.class})
@Sql("/sql/counter-test-data.sql")
class FeatureExtractionPipelineTest {

    private static final long YASUO = 157L;
    private static final long DOMINIK = 3036L;
    private static final long INFINITY_EDGE = 3031L;

    @Autowired
    private ItemStatsAggregationService aggregationService;
    @Autowired
    private FeatureExtractionPipeline pipeline;

    @BeforeEach
    void aggregate() {
        aggregationService.aggregate(1);
    }

    private RecommendationQuery query() {
        return new RecommendationQuery(
                YASUO, ChampionPosition.MID, List.of(),
                List.of(222L, 412L, 64L, 516L), List.of(33L, 103L, 51L, 89L, 60L),
                "EMERALD", "16.17");
    }

    private CandidateUnion union() {
        return CandidateUnion.merge(List.of(
                GeneratorResult.of(CandidateSource.BUILD, List.of(
                        new ScoredItem(DOMINIK, 0.9), new ScoredItem(INFINITY_EDGE, 0.5))),
                GeneratorResult.of(CandidateSource.COUNTER, List.of(new ScoredItem(DOMINIK, 0.7)))
        ));
    }

    @Test
    @DisplayName("후보마다 벡터를 하나씩 만든다")
    void extract_WhenUnionGiven_ProducesOneVectorPerCandidate() {
        // when
        List<CandidateFeatures> extracted = pipeline.extract(union(), query());

        // then
        assertThat(extracted).hasSize(2);
        assertThat(extracted).extracting(CandidateFeatures::itemId)
                .containsExactlyInAnyOrder(DOMINIK, INFINITY_EDGE);
    }

    @Test
    @DisplayName("세 출처의 feature가 한 벡터에 모두 담긴다 — 후보·통계·질의")
    void extract_WhenExtracted_CombinesCandidateStatsAndQueryFeatures() {
        // when
        FeatureVector vector = pipeline.extract(union(), query()).stream()
                .filter(features -> features.itemId() == DOMINIK)
                .findFirst().orElseThrow()
                .vector();

        // then
        assertThat(vector.get(FeatureName.BUILD_SCORE)).isEqualTo(0.9);            // 후보
        assertThat(vector.get(FeatureName.CHAMPION_BASE_RATE_ALL)).isEqualTo(0.45); // 통계
        assertThat(vector.get(FeatureName.POSITION_MID)).isEqualTo(1.0);            // 질의
    }

    @Test
    @DisplayName("벡터 길이는 항상 스키마 크기와 같다 — Python이 이 길이를 그대로 받는다")
    void extract_WhenExtracted_VectorLengthAlwaysMatchesSchema() {
        for (CandidateFeatures features : pipeline.extract(union(), query())) {
            assertThat(features.vector().values()).hasSize(FeatureName.values().length);
        }
    }

    @Test
    @DisplayName("같은 입력이면 항상 같은 벡터가 나온다 — 학습과 서빙이 같은 코드를 통과해야 skew가 없다")
    void extract_WhenCalledTwice_ProducesIdenticalVectors() {
        // when
        double[] first = pipeline.extract(union(), query()).get(0).vector().values();
        double[] second = pipeline.extract(union(), query()).get(0).vector().values();

        // then
        assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("후보가 없으면 빈 목록을 낸다")
    void extract_WhenUnionIsEmpty_ProducesNothing() {
        assertThat(pipeline.extract(CandidateUnion.merge(List.of()), query())).isEmpty();
    }
}
