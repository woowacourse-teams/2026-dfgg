package dfgg.application.recommend;

import dfgg.application.utils.CosineSimilarityCalculator;
import dfgg.domain.embedding.Embedding;
import dfgg.domain.embedding.EmbeddingEntityType;
import dfgg.domain.embedding.EmbeddingRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 카운터 임베딩 공간에서 적 챔피언들과의 코사인 유사도 중 최댓값(maxSim)이 가장 높은
 * 아이템을 PrefixSpan 통계와 무관하게 뽑아내는 "20% 탐색 구역" 후보를 만든다.
 */
@Component
public class ExplorationZoneCandidateGenerator {

    private final EmbeddingRepository embeddingRepository;
    private final CosineSimilarityCalculator cosineSimilarityCalculator;

    public ExplorationZoneCandidateGenerator(
            EmbeddingRepository embeddingRepository,
            CosineSimilarityCalculator cosineSimilarityCalculator
    ) {
        this.embeddingRepository = embeddingRepository;
        this.cosineSimilarityCalculator = cosineSimilarityCalculator;
    }

    public List<RankedItemCandidate> rankByMaxSimilarityToEnemies(
            List<Long> enemyChampionIds,
            String algorithmVersion
    ) {
        List<Embedding> enemyEmbeddings = embeddingRepository.findByAlgorithmVersionAndEntityTypeAndEntityIdIn(
                algorithmVersion, EmbeddingEntityType.CHAMPION, enemyChampionIds
        );
        if (enemyEmbeddings.isEmpty()) {
            return List.of();
        }

        List<Embedding> itemEmbeddings = embeddingRepository.findByAlgorithmVersionAndEntityType(
                algorithmVersion, EmbeddingEntityType.ITEM
        );

        return itemEmbeddings.stream()
                .map(item -> new RankedItemCandidate(item.getEntityId(), maxSimilarity(item, enemyEmbeddings)))
                .sorted(Comparator.comparingDouble(RankedItemCandidate::maxSimilarity).reversed())
                .toList();
    }

    private double maxSimilarity(Embedding item, List<Embedding> enemyEmbeddings) {
        return enemyEmbeddings.stream()
                .mapToDouble(enemy -> cosineSimilarityCalculator.compute(item.getVector(), enemy.getVector()))
                .max()
                .getAsDouble();
    }
}
