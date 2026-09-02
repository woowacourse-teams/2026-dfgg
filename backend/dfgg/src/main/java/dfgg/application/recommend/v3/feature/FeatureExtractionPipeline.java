package dfgg.application.recommend.v3.feature;

import dfgg.application.recommend.v3.CandidateUnion;
import dfgg.application.recommend.v3.ItemCandidate;
import dfgg.application.recommend.v3.RecommendationQuery;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * feature 추출의 단일 진입점.
 * 학습 데이터 export와 서빙이 모두 이 클래스를 통과한다.
 * <p>
 * 이게 이 설계의 핵심 제약이다. — Python은 feature를 계산하지 않고 여기서 내보낸 벡터를 그대로 학습에 쓴다.
 * 두 벌의 구현이 존재하면 학습과 서빙이 서서히 어긋나는데(train/serve skew),
 * 그 어긋남은 오프라인 지표로는 드러나지 않고 배포 후에야 나타난다.
 * <p>
 * 질의 단위 feature는 후보마다 다시 계산하지 않고 한 번만 뽑아 재사용한다
 * — 팀 조합 태그 조회가 후보 수만큼 반복되면 100배 낭비다.
 */
@Component
public class FeatureExtractionPipeline {

    private final CandidateFeatureExtractor candidateFeatureExtractor;
    private final StatsFeatureExtractor statsFeatureExtractor;
    private final QueryFeatureExtractor queryFeatureExtractor;

    public FeatureExtractionPipeline(
            CandidateFeatureExtractor candidateFeatureExtractor,
            StatsFeatureExtractor statsFeatureExtractor,
            QueryFeatureExtractor queryFeatureExtractor
    ) {
        this.candidateFeatureExtractor = candidateFeatureExtractor;
        this.statsFeatureExtractor = statsFeatureExtractor;
        this.queryFeatureExtractor = queryFeatureExtractor;
    }

    public List<CandidateFeatures> extract(CandidateUnion union, RecommendationQuery query) {
        if (union.isEmpty()) {
            return List.of();
        }
        // 질의 단위 feature는 모든 후보가 공유하므로 한 번만 계산한다.
        FeatureVector queryFeatures = FeatureVector.empty();
        queryFeatureExtractor.extract(query, queryFeatures);

        List<CandidateFeatures> extracted = new ArrayList<>(union.size());
        for (ItemCandidate candidate : union.candidates()) {
            FeatureVector vector = FeatureVector.copyOf(queryFeatures);
            candidateFeatureExtractor.extract(candidate, vector);
            statsFeatureExtractor.extract(candidate.itemId(), query, vector);
            extracted.add(new CandidateFeatures(candidate.itemId(), vector));
        }
        return extracted;
    }
}
