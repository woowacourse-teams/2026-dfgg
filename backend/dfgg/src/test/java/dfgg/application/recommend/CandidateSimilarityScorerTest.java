package dfgg.application.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
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

class CandidateSimilarityScorerTest {

    private static final String IDENTITY_VERSION = "checkpoint-a-4";
    private static final String COUNTER_VERSION = "checkpoint-c-1-counter";

    private EmbeddingRepository embeddingRepository;
    private CandidateSimilarityScorer scorer;

    @BeforeEach
    void setUp() {
        embeddingRepository = mock(EmbeddingRepository.class);
        scorer = new CandidateSimilarityScorer(embeddingRepository, new CosineSimilarityCalculator());
    }

    private Embedding embeddingOf(EmbeddingEntityType type, Long entityId, String algorithmVersion, List<Double> vector) {
        return new Embedding(type, entityId, algorithmVersion, vector, LocalDateTime.now());
    }

    @Test
    @DisplayName("정체성 공간에서 내 챔피언·아군과의 유사도, 카운터 공간에서 적군과의 유사도를 각각 계산한다")
    void scoreItems_WhenAllEmbeddingsExist_ComputesAllThreeSimilarities() {
        // given: 아이템 벡터는 [1,0] — 내 챔피언과는 완전 일치(1.0), 아군 중 [0,1]과는 직교(0.0)
        // 카운터 공간의 같은 아이템은 [0.6,0.8] — 적군 [1,0]과의 유사도는 0.6
        when(embeddingRepository.findByAlgorithmVersionAndEntityTypeAndEntityIdIn(
                IDENTITY_VERSION, EmbeddingEntityType.ITEM, List.of(3031L)
        )).thenReturn(List.of(embeddingOf(EmbeddingEntityType.ITEM, 3031L, IDENTITY_VERSION, List.of(1.0, 0.0))));
        when(embeddingRepository.findByAlgorithmVersionAndEntityTypeAndEntityIdIn(
                IDENTITY_VERSION, EmbeddingEntityType.CHAMPION, List.of(222L, 412L)
        )).thenReturn(List.of(
                embeddingOf(EmbeddingEntityType.CHAMPION, 222L, IDENTITY_VERSION, List.of(1.0, 0.0)),
                embeddingOf(EmbeddingEntityType.CHAMPION, 412L, IDENTITY_VERSION, List.of(0.0, 1.0))
        ));
        when(embeddingRepository.findByAlgorithmVersionAndEntityTypeAndEntityIdIn(
                COUNTER_VERSION, EmbeddingEntityType.ITEM, List.of(3031L)
        )).thenReturn(List.of(embeddingOf(EmbeddingEntityType.ITEM, 3031L, COUNTER_VERSION, List.of(0.6, 0.8))));
        when(embeddingRepository.findByAlgorithmVersionAndEntityTypeAndEntityIdIn(
                COUNTER_VERSION, EmbeddingEntityType.CHAMPION, List.of(54L)
        )).thenReturn(List.of(embeddingOf(EmbeddingEntityType.CHAMPION, 54L, COUNTER_VERSION, List.of(1.0, 0.0))));

        // when
        List<ItemSimilarityScores> scores = scorer.scoreItems(
                List.of(3031L), 222L, List.of(412L), List.of(54L), IDENTITY_VERSION, COUNTER_VERSION
        );

        // then
        assertThat(scores).hasSize(1);
        ItemSimilarityScores score = scores.get(0);
        assertThat(score.itemId()).isEqualTo(3031L);
        assertThat(score.cosineToMyChampion()).isCloseTo(1.0, offset(0.0001));
        assertThat(score.maxSimilarityToAllies()).isCloseTo(0.0, offset(0.0001));
        assertThat(score.maxSimilarityToEnemies()).isCloseTo(0.6, offset(0.0001));
    }

    @Test
    @DisplayName("정체성 공간에 아이템 임베딩이 없으면 내 챔피언·아군 유사도는 0으로 처리한다")
    void scoreItems_WhenIdentityItemEmbeddingMissing_ReturnsZeroForIdentitySimilarities() {
        // given: 정체성 공간엔 이 아이템이 없음(신규 아이템 등), 카운터 공간엔 있음
        when(embeddingRepository.findByAlgorithmVersionAndEntityTypeAndEntityIdIn(
                IDENTITY_VERSION, EmbeddingEntityType.ITEM, List.of(9999L)
        )).thenReturn(List.of());
        when(embeddingRepository.findByAlgorithmVersionAndEntityTypeAndEntityIdIn(
                IDENTITY_VERSION, EmbeddingEntityType.CHAMPION, List.of(222L, 412L)
        )).thenReturn(List.of(
                embeddingOf(EmbeddingEntityType.CHAMPION, 222L, IDENTITY_VERSION, List.of(1.0, 0.0)),
                embeddingOf(EmbeddingEntityType.CHAMPION, 412L, IDENTITY_VERSION, List.of(0.0, 1.0))
        ));
        when(embeddingRepository.findByAlgorithmVersionAndEntityTypeAndEntityIdIn(
                COUNTER_VERSION, EmbeddingEntityType.ITEM, List.of(9999L)
        )).thenReturn(List.of(embeddingOf(EmbeddingEntityType.ITEM, 9999L, COUNTER_VERSION, List.of(0.6, 0.8))));
        when(embeddingRepository.findByAlgorithmVersionAndEntityTypeAndEntityIdIn(
                COUNTER_VERSION, EmbeddingEntityType.CHAMPION, List.of(54L)
        )).thenReturn(List.of(embeddingOf(EmbeddingEntityType.CHAMPION, 54L, COUNTER_VERSION, List.of(1.0, 0.0))));

        // when
        List<ItemSimilarityScores> scores = scorer.scoreItems(
                List.of(9999L), 222L, List.of(412L), List.of(54L), IDENTITY_VERSION, COUNTER_VERSION
        );

        // then
        ItemSimilarityScores score = scores.get(0);
        assertThat(score.cosineToMyChampion()).isEqualTo(0.0);
        assertThat(score.maxSimilarityToAllies()).isEqualTo(0.0);
        assertThat(score.maxSimilarityToEnemies()).isCloseTo(0.6, offset(0.0001));
    }

    @Test
    @DisplayName("아군이 없으면(빈 목록) 아군 유사도는 0으로 처리한다")
    void scoreItems_WhenNoAllies_ReturnsZeroForAllySimilarity() {
        // given
        when(embeddingRepository.findByAlgorithmVersionAndEntityTypeAndEntityIdIn(
                IDENTITY_VERSION, EmbeddingEntityType.ITEM, List.of(3031L)
        )).thenReturn(List.of(embeddingOf(EmbeddingEntityType.ITEM, 3031L, IDENTITY_VERSION, List.of(1.0, 0.0))));
        when(embeddingRepository.findByAlgorithmVersionAndEntityTypeAndEntityIdIn(
                IDENTITY_VERSION, EmbeddingEntityType.CHAMPION, List.of(222L)
        )).thenReturn(List.of(embeddingOf(EmbeddingEntityType.CHAMPION, 222L, IDENTITY_VERSION, List.of(1.0, 0.0))));
        when(embeddingRepository.findByAlgorithmVersionAndEntityTypeAndEntityIdIn(
                COUNTER_VERSION, EmbeddingEntityType.ITEM, List.of(3031L)
        )).thenReturn(List.of());
        when(embeddingRepository.findByAlgorithmVersionAndEntityTypeAndEntityIdIn(
                COUNTER_VERSION, EmbeddingEntityType.CHAMPION, List.of()
        )).thenReturn(List.of());

        // when
        List<ItemSimilarityScores> scores = scorer.scoreItems(
                List.of(3031L), 222L, List.of(), List.of(), IDENTITY_VERSION, COUNTER_VERSION
        );

        // then
        ItemSimilarityScores score = scores.get(0);
        assertThat(score.cosineToMyChampion()).isCloseTo(1.0, offset(0.0001));
        assertThat(score.maxSimilarityToAllies()).isEqualTo(0.0);
        assertThat(score.maxSimilarityToEnemies()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("여러 아이템을 한 번에 배치로 점수 매긴다")
    void scoreItems_WhenMultipleItemIds_ReturnsScoreForEachItem() {
        // given
        when(embeddingRepository.findByAlgorithmVersionAndEntityTypeAndEntityIdIn(
                IDENTITY_VERSION, EmbeddingEntityType.ITEM, List.of(3031L, 3072L)
        )).thenReturn(List.of(
                embeddingOf(EmbeddingEntityType.ITEM, 3031L, IDENTITY_VERSION, List.of(1.0, 0.0)),
                embeddingOf(EmbeddingEntityType.ITEM, 3072L, IDENTITY_VERSION, List.of(0.0, 1.0))
        ));
        when(embeddingRepository.findByAlgorithmVersionAndEntityTypeAndEntityIdIn(
                IDENTITY_VERSION, EmbeddingEntityType.CHAMPION, List.of(222L)
        )).thenReturn(List.of(embeddingOf(EmbeddingEntityType.CHAMPION, 222L, IDENTITY_VERSION, List.of(1.0, 0.0))));
        when(embeddingRepository.findByAlgorithmVersionAndEntityTypeAndEntityIdIn(
                COUNTER_VERSION, EmbeddingEntityType.ITEM, List.of(3031L, 3072L)
        )).thenReturn(List.of());
        when(embeddingRepository.findByAlgorithmVersionAndEntityTypeAndEntityIdIn(
                COUNTER_VERSION, EmbeddingEntityType.CHAMPION, List.of()
        )).thenReturn(List.of());

        // when
        List<ItemSimilarityScores> scores = scorer.scoreItems(
                List.of(3031L, 3072L), 222L, List.of(), List.of(), IDENTITY_VERSION, COUNTER_VERSION
        );

        // then
        assertThat(scores).extracting(ItemSimilarityScores::itemId).containsExactly(3031L, 3072L);
        assertThat(scores.get(0).cosineToMyChampion()).isCloseTo(1.0, offset(0.0001));
        assertThat(scores.get(1).cosineToMyChampion()).isCloseTo(0.0, offset(0.0001));
    }
}
