package dfgg.application.recommend.v3.feature;

import dfgg.application.recommend.v3.CandidateSource;
import dfgg.application.recommend.v3.ItemCandidate;
import dfgg.application.recommend.v3.SourceEvidence;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * 후보 자체가 들고 있는 근거를 feature로 옮긴다 — 어느 generator가 찾았는지, 몇 위로 몇 점을
 * 줬는지, 얼마나 백오프했는지.
 * <p>
 * 여기서 점수를 섞거나 가중치를 주지 않는다. 네 벌의 근거를 그대로 펼쳐 놓고,
 * 그 사이의 trade-off는 LambdaMART가 학습한다 — 이번 작업이 없애려는 게 정확히 그 수동 가중치다.
 * <p>
 * 찾지 못한 generator의 score·rank는 {@code NaN}으로 남긴다. 0으로 채우면 "counter가 0점으로
 * 평가했다"와 "counter가 아예 보지 않았다"가 같은 값이 되는데, 그 둘의 구분이 이번 작업의
 * 실패 지표(base rate가 낮은데 counter만으로 상승) 분석의 전제다. 대신 "찾지 않았다"는
 * 사실 자체는 {@code SOURCE_*}에 0으로 명시된다.
 */
@Component
public class CandidateFeatureExtractor {

    private static final Map<CandidateSource, SourceFeatures> FEATURES_BY_SOURCE =
            new EnumMap<>(Map.of(
                    CandidateSource.BUILD, new SourceFeatures(
                            FeatureName.SOURCE_BUILD, FeatureName.BUILD_SCORE,
                            FeatureName.BUILD_RANK, FeatureName.BUILD_BACKOFF_LEVEL),
                    CandidateSource.SELF_SYNERGY, new SourceFeatures(
                            FeatureName.SOURCE_SELF_SYNERGY, FeatureName.SELF_SYNERGY_SCORE,
                            FeatureName.SELF_SYNERGY_RANK, FeatureName.SELF_SYNERGY_BACKOFF_LEVEL),
                    CandidateSource.ALLY_SYNERGY, new SourceFeatures(
                            FeatureName.SOURCE_ALLY_SYNERGY, FeatureName.ALLY_SYNERGY_SCORE,
                            FeatureName.ALLY_SYNERGY_RANK, FeatureName.ALLY_SYNERGY_BACKOFF_LEVEL),
                    CandidateSource.COUNTER, new SourceFeatures(
                            FeatureName.SOURCE_COUNTER, FeatureName.COUNTER_SCORE,
                            FeatureName.COUNTER_RANK, FeatureName.COUNTER_BACKOFF_LEVEL)
            ));

    public void extract(ItemCandidate candidate, FeatureVector vector) {
        for (CandidateSource source : CandidateSource.values()) {
            SourceFeatures features = FEATURES_BY_SOURCE.get(source);
            Optional<SourceEvidence> evidence = candidate.evidenceOf(source);

            vector.set(features.flag(), evidence.isPresent() ? 1.0 : 0.0);
            evidence.ifPresent(found -> {
                vector.set(features.score(), found.score());
                vector.set(features.rank(), found.rank());
                vector.set(features.backoffLevel(), found.backoffLevel());
            });
        }
    }

    private record SourceFeatures(
            FeatureName flag,
            FeatureName score,
            FeatureName rank,
            FeatureName backoffLevel
    ) {
    }
}
