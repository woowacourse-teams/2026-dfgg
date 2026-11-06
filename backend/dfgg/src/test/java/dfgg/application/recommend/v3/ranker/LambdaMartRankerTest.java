package dfgg.application.recommend.v3.ranker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import dfgg.application.recommend.v3.CandidateUnion;
import dfgg.application.recommend.v3.RecommendationQuery;
import dfgg.application.recommend.v3.feature.CandidateFeatures;
import dfgg.application.recommend.v3.feature.FeatureExtractionPipeline;
import dfgg.application.recommend.v3.feature.FeatureName;
import dfgg.application.recommend.v3.feature.FeatureVector;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 최종 순위를 모델 점수 <b>단독으로</b> 정한다.
 *
 * <p>수동 가중합·context별 boost·counter override는 두지 않는다. generator는 후보를 찾을 뿐이고
 * 근거들 사이의 trade-off는 학습된 모델이 정한다.
 */
@ExtendWith(MockitoExtension.class)
class LambdaMartRankerTest {

    @Mock
    private FeatureExtractionPipeline pipeline;

    private GradientBoostedTrees scoreIsFirstFeature;

    @BeforeEach
    void setUp() {
        // f0 <= 0.5 → 0.0, 아니면 1.0. 첫 feature만으로 점수가 갈린다.
        scoreIsFirstFeature = new GradientBoostedTrees(List.of(new DecisionTree(
                new int[]{0}, new double[]{0.5}, new boolean[]{true},
                new int[]{-1}, new int[]{-2}, new double[]{0.0, 1.0},
                new double[]{10.0}, new double[]{6.0, 4.0})));
    }

    private CandidateFeatures candidate(long itemId, double firstFeature) {
        FeatureVector vector = FeatureVector.empty();
        vector.set(FeatureName.values()[0], firstFeature);
        return new CandidateFeatures(itemId, vector);
    }

    private List<Long> itemIdsOf(List<RankedCandidate> ranked) {
        return ranked.stream().map(RankedCandidate::itemId).toList();
    }

    private LambdaMartRanker ranker() {
        return new LambdaMartRanker(pipeline, scoreIsFirstFeature);
    }

    @Test
    @DisplayName("모델 점수가 높은 순으로 정렬한다")
    void rank_OrdersByModelScoreDescending() {
        given(pipeline.extract(any(), any())).willReturn(List.of(
                candidate(100L, 0.1), candidate(200L, 0.9), candidate(300L, 0.2)));

        List<RankedCandidate> ranked = ranker().rank(CandidateUnion.merge(List.of()), null, 5);

        assertThat(itemIdsOf(ranked)).startsWith(200L);
    }

    @Test
    @DisplayName("요청한 개수만큼만 낸다")
    void rank_LimitsToTopN() {
        given(pipeline.extract(any(), any())).willReturn(List.of(
                candidate(100L, 0.9), candidate(200L, 0.8), candidate(300L, 0.7)));

        assertThat(ranker().rank(CandidateUnion.merge(List.of()), null, 2)).hasSize(2);
    }

    @Test
    @DisplayName("후보가 요청 개수보다 적으면 있는 만큼만 낸다")
    void rank_WhenFewerCandidatesThanTopN_ReturnsAll() {
        given(pipeline.extract(any(), any())).willReturn(List.of(candidate(100L, 0.9)));

        assertThat(itemIdsOf(ranker().rank(CandidateUnion.merge(List.of()), null, 5))).containsExactly(100L);
    }

    @Test
    @DisplayName("후보가 없으면 빈 결과를 낸다")
    void rank_WhenNoCandidates_ReturnsEmpty() {
        given(pipeline.extract(any(), any())).willReturn(List.of());

        assertThat(ranker().rank(CandidateUnion.merge(List.of()), null, 5)).isEmpty();
    }

    @Test
    @DisplayName("점수가 같으면 itemId로 갈라 매번 같은 순서를 낸다 — 순서가 흔들리면 지표를 믿을 수 없다")
    void rank_WhenScoresTie_BreaksDeterministicallyByItemId() {
        given(pipeline.extract(any(), any())).willReturn(List.of(
                candidate(300L, 0.9), candidate(100L, 0.9), candidate(200L, 0.9)));

        assertThat(itemIdsOf(ranker().rank(CandidateUnion.merge(List.of()), null, 3)))
                .containsExactly(100L, 200L, 300L);
    }

    @Test
    @DisplayName("결측 feature도 그대로 모델에 넘긴다 — 0으로 채우면 '값이 0'이라고 거짓말하게 된다")
    void rank_PassesMissingFeaturesThroughAsNaN() {
        GradientBoostedTrees missingGoesRight = new GradientBoostedTrees(List.of(new DecisionTree(
                new int[]{0}, new double[]{0.5}, new boolean[]{false},
                new int[]{-1}, new int[]{-2}, new double[]{0.0, 1.0},
                new double[]{10.0}, new double[]{6.0, 4.0})));
        // 채우지 않은 벡터는 전부 결측이다
        given(pipeline.extract(any(), any())).willReturn(List.of(
                new CandidateFeatures(100L, FeatureVector.empty()), candidate(200L, 0.1)));

        List<RankedCandidate> ranked = new LambdaMartRanker(pipeline, missingGoesRight)
                .rank(CandidateUnion.merge(List.of()), null, 2);

        // 결측이 오른쪽(1.0)으로 가야 100번이 앞선다. 0으로 채웠다면 0.0이 되어 뒤로 밀린다.
        assertThat(itemIdsOf(ranked)).containsExactly(100L, 200L);
    }

    @Test
    @DisplayName("순위와 함께 각 후보의 feature 벡터를 돌려준다 — 추천 근거를 만들려면 필요하다")
    void rank_ReturnsTheFeatureVectorAlongsideEachItem() {
        given(pipeline.extract(any(), any())).willReturn(List.of(candidate(200L, 0.9)));

        List<RankedCandidate> ranked = ranker().rank(CandidateUnion.merge(List.of()), null, 5);

        assertThat(ranked.getFirst().features().get(FeatureName.values()[0])).isEqualTo(0.9);
    }

    @Test
    @DisplayName("모델 점수를 함께 돌려준다 — 순위가 왜 그렇게 나왔는지 관측할 수 있어야 한다")
    void rank_ReturnsTheModelScore() {
        given(pipeline.extract(any(), any())).willReturn(List.of(
                candidate(100L, 0.1), candidate(200L, 0.9)));

        List<RankedCandidate> ranked = ranker().rank(CandidateUnion.merge(List.of()), null, 5);

        assertThat(ranked.getFirst().modelScore()).isGreaterThan(ranked.getLast().modelScore());
    }

    @Test
    @DisplayName("modelVersion에 스키마 지문이 들어간다 — 어떤 스키마의 모델이 순위를 냈는지 남긴다")
    void modelVersion_ContainsSchemaFingerprint() {
        assertThat(ranker().modelVersion()).contains(FeatureName.schemaFingerprint());
    }

    @Test
    @DisplayName("실제 커밋된 모델로도 순위가 나온다")
    void rank_WorksWithTheCommittedModel() {
        given(pipeline.extract(any(), any())).willReturn(List.of(
                candidate(100L, 0.1), candidate(200L, 0.9)));
        GradientBoostedTrees real = LightGbmModelLoader.loadFromClasspath("ltr/model.json");

        List<RankedCandidate> ranked = new LambdaMartRanker(pipeline, real)
                .rank(CandidateUnion.merge(List.of()), (RecommendationQuery) null, 2);

        assertThat(itemIdsOf(ranked)).containsExactlyInAnyOrder(100L, 200L);
    }
}
