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
    private final String schemaFingerprint;

    /** 지금 스키마 전체로 학습된 모델의 랭커. */
    public LambdaMartRanker(FeatureExtractionPipeline pipeline, GradientBoostedTrees model) {
        this(pipeline, model, FeatureName.schemaFingerprint());
    }

    /**
     * @param schemaFingerprint 모델이 학습된 계약의 지문. 모델 버전은 지금 스키마가 아니라 모델을 따라간다
     *                          — 스키마 뒤에 feature를 더해도 같은 모델이면 버전이 그대로여야 한다.
     */
    public LambdaMartRanker(FeatureExtractionPipeline pipeline, GradientBoostedTrees model, String schemaFingerprint) {
        this.pipeline = pipeline;
        this.model = model;
        this.schemaFingerprint = schemaFingerprint;
    }

    @Override
    public List<RankedCandidate> rank(CandidateUnion union, RecommendationQuery query, int topN) {
        return pipeline.extract(union, query).stream()
                .sorted(Comparator.comparingDouble(this::score).reversed()
                        .thenComparing(CandidateFeatures::itemId))
                .limit(topN)
                .map(candidate -> new RankedCandidate(
                        candidate.itemId(), score(candidate), candidate.vector()))
                .toList();
    }

    @Override
    public String modelVersion() {
        return "lambdamart-" + schemaFingerprint;
    }

    private double score(CandidateFeatures candidate) {
        return model.predict(candidate.vector().values());
    }
}
