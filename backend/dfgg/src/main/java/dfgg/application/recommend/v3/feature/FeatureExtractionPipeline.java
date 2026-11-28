package dfgg.application.recommend.v3.feature;

import dfgg.application.recommend.v3.CandidateUnion;
import dfgg.application.recommend.v3.ItemCandidate;
import dfgg.application.recommend.v3.RecommendationQuery;
import dfgg.domain.item.Item;
import dfgg.domain.item.ItemRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final ItemRepository items;
    /** 상태도 의존성도 없는 계산 객체라 빈으로 두지 않고 직접 갖는다. 후보 아이템 조회는 여기서 해서 넘긴다. */
    private final SituationMatchFeatureExtractor situationMatchFeatureExtractor = new SituationMatchFeatureExtractor();

    public FeatureExtractionPipeline(
            CandidateFeatureExtractor candidateFeatureExtractor,
            StatsFeatureExtractor statsFeatureExtractor,
            QueryFeatureExtractor queryFeatureExtractor,
            ItemRepository items
    ) {
        this.candidateFeatureExtractor = candidateFeatureExtractor;
        this.statsFeatureExtractor = statsFeatureExtractor;
        this.queryFeatureExtractor = queryFeatureExtractor;
        this.items = items;
    }

    public List<CandidateFeatures> extract(CandidateUnion union, RecommendationQuery query) {
        if (union.isEmpty()) {
            return List.of();
        }
        // 질의 단위 feature는 모든 후보가 공유하므로 한 번만 계산한다.
        FeatureVector queryFeatures = FeatureVector.empty();
        queryFeatureExtractor.extract(query, queryFeatures);
        StatsFeatureExtractor.StatsContext statsContext = statsFeatureExtractor.prepare(query);
        Map<Long, Item> candidateItems = candidateItemsFor(union, query);

        List<CandidateFeatures> extracted = new ArrayList<>(union.size());
        for (ItemCandidate candidate : union.candidates()) {
            FeatureVector vector = FeatureVector.copyOf(queryFeatures);
            candidateFeatureExtractor.extract(candidate, vector);
            statsFeatureExtractor.extract(candidate.itemId(), query, statsContext, vector);
            situationMatchFeatureExtractor.extract(candidate.itemId(), candidateItems, vector);
            extracted.add(new CandidateFeatures(candidate.itemId(), vector));
        }
        return extracted;
    }

    /**
     * 상황 맞춤 값에 쓸 후보 아이템. 요청당 한 번만 읽는다.
     * 상황을 모르면(v3·v4) 맞춤 값을 낼 수 없으므로 읽지 않는다 — 빈 묶음이면 맞춤 칸이 비어 있게 된다.
     */
    private Map<Long, Item> candidateItemsFor(CandidateUnion union, RecommendationQuery query) {
        if (query.situation().isUnknown()) {
            return Map.of();
        }
        Set<Long> candidateIds = union.candidates().stream()
                .map(ItemCandidate::itemId)
                .collect(Collectors.toSet());
        return items.findAllById(candidateIds).stream()
                .collect(Collectors.toMap(Item::getItemId, Function.identity()));
    }
}
