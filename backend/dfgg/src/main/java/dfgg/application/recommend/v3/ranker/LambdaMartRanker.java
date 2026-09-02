package dfgg.application.recommend.v3.ranker;

import dfgg.application.recommend.v3.CandidateUnion;
import dfgg.application.recommend.v3.RecommendationQuery;
import dfgg.application.recommend.v3.feature.CandidateFeatures;
import dfgg.application.recommend.v3.feature.FeatureExtractionPipeline;
import dfgg.application.recommend.v3.feature.FeatureName;
import java.util.Comparator;
import java.util.List;

/**
 * 학습된 LambdaMART로 최종 순위를 정한다.
 *
 * <p>근거들 사이의 trade-off는 전부 모델이 정한다. 수동 가중합·context별 boost·counter override는
 * 두지 않는다 — 그것들이 이번 작업이 없애려던 것이다.
 *
 * <p>모델 점수는 Python 학습 시점의 점수와 1e-6 이내로 일치함이 {@link LightGbmParityTest}에서
 * 보장된다.
 */
public class LambdaMartRanker implements CandidateRanker {

    private final FeatureExtractionPipeline pipeline;
    private final GradientBoostedTrees model;

    public LambdaMartRanker(FeatureExtractionPipeline pipeline, GradientBoostedTrees model) {
        this.pipeline = pipeline;
        this.model = model;
    }

    @Override
    public List<Long> rank(CandidateUnion union, RecommendationQuery query, int topN) {
        return pipeline.extract(union, query).stream()
                .sorted(Comparator.comparingDouble(this::score).reversed()
                        .thenComparing(CandidateFeatures::itemId))
                .limit(topN)
                .map(CandidateFeatures::itemId)
                .toList();
    }

    @Override
    public String modelVersion() {
        return "lambdamart-" + FeatureName.schemaFingerprint();
    }

    private double score(CandidateFeatures candidate) {
        return model.predict(candidate.vector().values());
    }
}
