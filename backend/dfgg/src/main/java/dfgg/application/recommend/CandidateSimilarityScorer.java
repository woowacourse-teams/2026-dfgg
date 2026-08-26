package dfgg.application.recommend;

import dfgg.application.utils.CosineSimilarityCalculator;
import dfgg.domain.embedding.Embedding;
import dfgg.domain.embedding.EmbeddingEntityType;
import dfgg.domain.embedding.EmbeddingRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 다음 아이템 후보들에 대해 정체성 공간(내 챔피언·아군)과 카운터 공간(적군) 유사도를
 * 한 번에 배치로 계산한다. 후보마다 리포지토리를 따로 호출하지 않도록 임베딩을 미리
 * 한 번씩만 조회한다.
 */
@Component
public class CandidateSimilarityScorer {

    private final EmbeddingRepository embeddingRepository;
    private final CosineSimilarityCalculator cosineSimilarityCalculator;

    public CandidateSimilarityScorer(
            EmbeddingRepository embeddingRepository,
            CosineSimilarityCalculator cosineSimilarityCalculator
    ) {
        this.embeddingRepository = embeddingRepository;
        this.cosineSimilarityCalculator = cosineSimilarityCalculator;
    }

    public List<ItemSimilarityScores> scoreItems(
            List<Long> itemIds,
            Long myChampionId,
            List<Long> allyChampionIds,
            List<Long> enemyChampionIds,
            String identityAlgorithmVersion,
            String counterAlgorithmVersion
    ) {
        Map<Long, List<Double>> identityItemVectors =
                fetchVectors(identityAlgorithmVersion, EmbeddingEntityType.ITEM, itemIds);
        Map<Long, List<Double>> identityChampionVectors = fetchVectors(
                identityAlgorithmVersion, EmbeddingEntityType.CHAMPION, allyIdsWithMyChampion(myChampionId, allyChampionIds)
        );
        Map<Long, List<Double>> counterItemVectors =
                fetchVectors(counterAlgorithmVersion, EmbeddingEntityType.ITEM, itemIds);
        Map<Long, List<Double>> counterChampionVectors =
                fetchVectors(counterAlgorithmVersion, EmbeddingEntityType.CHAMPION, enemyChampionIds);

        List<Double> myChampionVector = identityChampionVectors.get(myChampionId);
        List<List<Double>> allyVectors = vectorsOf(allyChampionIds, identityChampionVectors);
        List<List<Double>> enemyVectors = vectorsOf(enemyChampionIds, counterChampionVectors);

        return itemIds.stream()
                .map(itemId -> new ItemSimilarityScores(
                        itemId,
                        cosineToMyChampion(identityItemVectors.get(itemId), myChampionVector),
                        maxSimilarityOrZero(identityItemVectors.get(itemId), allyVectors),
                        maxSimilarityOrZero(counterItemVectors.get(itemId), enemyVectors)
                ))
                .toList();
    }

    private List<Long> allyIdsWithMyChampion(Long myChampionId, List<Long> allyChampionIds) {
        List<Long> ids = new ArrayList<>();
        ids.add(myChampionId);
        ids.addAll(allyChampionIds);
        return ids;
    }

    private List<List<Double>> vectorsOf(List<Long> championIds, Map<Long, List<Double>> vectorsById) {
        return championIds.stream()
                .map(vectorsById::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private double cosineToMyChampion(List<Double> itemVector, List<Double> myChampionVector) {
        if (itemVector == null || myChampionVector == null) {
            return 0.0;
        }
        return cosineSimilarityCalculator.compute(itemVector, myChampionVector);
    }

    private double maxSimilarityOrZero(List<Double> itemVector, List<List<Double>> teamVectors) {
        if (itemVector == null || teamVectors.isEmpty()) {
            return 0.0;
        }
        return cosineSimilarityCalculator.maxSimilarity(itemVector, teamVectors);
    }

    private Map<Long, List<Double>> fetchVectors(
            String algorithmVersion, EmbeddingEntityType entityType, List<Long> entityIds
    ) {
        Map<Long, List<Double>> vectorsById = new HashMap<>();
        for (Embedding embedding : embeddingRepository.findByAlgorithmVersionAndEntityTypeAndEntityIdIn(
                algorithmVersion, entityType, entityIds
        )) {
            vectorsById.put(embedding.getEntityId(), embedding.getVector());
        }
        return vectorsById;
    }
}
