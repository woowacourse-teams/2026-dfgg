package dfgg.application.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dfgg.application.utils.CosineSimilarityCalculator;
import dfgg.domain.embedding.Embedding;
import dfgg.domain.embedding.EmbeddingEntityType;
import dfgg.domain.embedding.EmbeddingRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExplorationZoneCandidateGeneratorTest {

    private EmbeddingRepository embeddingRepository;
    private ExplorationZoneCandidateGenerator explorationZoneCandidateGenerator;

    @BeforeEach
    void setUp() {
        embeddingRepository = mock(EmbeddingRepository.class);
        explorationZoneCandidateGenerator = new ExplorationZoneCandidateGenerator(
                embeddingRepository, new CosineSimilarityCalculator()
        );
    }

    @Test
    @DisplayName("적 챔피언 여러 명 중 코사인 유사도가 가장 높은 값을 아이템의 점수로 삼아 내림차순 정렬한다")
    void rankByMaxSimilarityToEnemies_WhenMultipleEnemies_ScoresByMaxSimilarityDescending() {
        // given
        Embedding enemyOne = new Embedding(EmbeddingEntityType.CHAMPION, 101L, "counter-v1", List.of(1.0, 0.0), LocalDateTime.now());
        Embedding enemyTwo = new Embedding(EmbeddingEntityType.CHAMPION, 102L, "counter-v1", List.of(0.0, 1.0), LocalDateTime.now());
        // itemA는 enemyOne과만 완전히 같은 방향(유사도 1.0), enemyTwo와는 직교(유사도 0.0) → max = 1.0
        Embedding itemA = new Embedding(EmbeddingEntityType.ITEM, 3001L, "counter-v1", List.of(1.0, 0.0), LocalDateTime.now());
        // itemB는 두 적 모두와 어느 정도 유사(0.6, 0.8) → max = 0.8
        Embedding itemB = new Embedding(EmbeddingEntityType.ITEM, 3002L, "counter-v1", List.of(0.6, 0.8), LocalDateTime.now());

        when(embeddingRepository.findByAlgorithmVersionAndEntityTypeAndEntityIdIn(
                eq("counter-v1"), eq(EmbeddingEntityType.CHAMPION), eq(List.of(101L, 102L))
        )).thenReturn(List.of(enemyOne, enemyTwo));
        when(embeddingRepository.findByAlgorithmVersionAndEntityType("counter-v1", EmbeddingEntityType.ITEM))
                .thenReturn(List.of(itemA, itemB));

        // when
        List<RankedItemCandidate> ranked = explorationZoneCandidateGenerator.rankByMaxSimilarityToEnemies(
                List.of(101L, 102L), "counter-v1"
        );

        // then
        assertThat(ranked).extracting(RankedItemCandidate::itemId)
                .containsExactly(3001L, 3002L);
        assertThat(ranked.get(0).score()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(ranked.get(1).score()).isCloseTo(0.8, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    @DisplayName("적 챔피언 임베딩을 하나도 찾지 못하면 빈 리스트를 반환한다")
    void rankByMaxSimilarityToEnemies_WhenNoEnemyEmbeddingsFound_ReturnsEmptyList() {
        // given
        when(embeddingRepository.findByAlgorithmVersionAndEntityTypeAndEntityIdIn(
                eq("counter-v1"), eq(EmbeddingEntityType.CHAMPION), eq(List.of(999L))
        )).thenReturn(List.of());

        // when
        List<RankedItemCandidate> ranked = explorationZoneCandidateGenerator.rankByMaxSimilarityToEnemies(
                List.of(999L), "counter-v1"
        );

        // then
        assertThat(ranked).isEmpty();
    }
}
