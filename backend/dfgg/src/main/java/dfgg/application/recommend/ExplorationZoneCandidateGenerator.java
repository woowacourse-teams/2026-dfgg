package dfgg.application.recommend;

import dfgg.application.ChampionPositionNormalizer;
import dfgg.application.utils.CosineSimilarityCalculator;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.embedding.Embedding;
import dfgg.domain.embedding.EmbeddingEntityType;
import dfgg.domain.embedding.EmbeddingRepository;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 카운터 임베딩 공간에서 적 챔피언들과의 코사인 유사도 중 최댓값(maxSim)이 가장 높은
 * 아이템을 PrefixSpan 통계와 무관하게 뽑아내는 "20% 탐색 구역" 후보를 만든다.
 *
 * <p>카운터 공간은 태그를 학습하지 않는다(콘텐츠 문맥은 정체성 공간 전용, {@code
 * EmbeddingTrainingBatchService.trainCounterEmbeddingsFromMatchData}는 {@code
 * buildContentContextWindows}를 호출하지 않음). 그래서 이 공간의 유사도만으로는 "이
 * 챔피언이 애초에 사지 않는 아이템"(예: 원거리 딜러에게 나온 엔챈터 전용 서포트
 * 아이템)을 걸러낼 수 없다 — 실측 구매 이력({@link
 * NormalizedMatchParticipantRepository#findDistinctPurchasedItemIds})으로 후보를
 * 이 챔피언·포지션이 실제로 산 적 있는 아이템으로 제한한다.
 */
@Component
public class ExplorationZoneCandidateGenerator {

    private final EmbeddingRepository embeddingRepository;
    private final NormalizedMatchParticipantRepository participantRepository;
    private final CosineSimilarityCalculator cosineSimilarityCalculator;
    private final ChampionPositionNormalizer positionNormalizer;

    public ExplorationZoneCandidateGenerator(
            EmbeddingRepository embeddingRepository,
            NormalizedMatchParticipantRepository participantRepository,
            CosineSimilarityCalculator cosineSimilarityCalculator,
            ChampionPositionNormalizer positionNormalizer
    ) {
        this.embeddingRepository = embeddingRepository;
        this.participantRepository = participantRepository;
        this.cosineSimilarityCalculator = cosineSimilarityCalculator;
        this.positionNormalizer = positionNormalizer;
    }

    public List<RankedItemCandidate> rankByMaxSimilarityToEnemies(
            List<Long> enemyChampionIds,
            String algorithmVersion,
            Long myChampionId,
            ChampionPosition position
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

        List<List<Double>> enemyVectors = enemyEmbeddings.stream().map(Embedding::getVector).toList();
        Set<Long> purchasedItemIds = participantRepository
                .findDistinctPurchasedItemIds(myChampionId, positionNormalizer.riotValuesOf(position))
                .stream()
                .map(Long::valueOf)
                .collect(Collectors.toSet());

        return itemEmbeddings.stream()
                .filter(item -> purchasedItemIds.contains(item.getEntityId()))
                .map(item -> new RankedItemCandidate(
                        item.getEntityId(),
                        cosineSimilarityCalculator.maxSimilarity(item.getVector(), enemyVectors)
                ))
                .sorted(Comparator.comparingDouble(RankedItemCandidate::score).reversed())
                .toList();
    }
}
