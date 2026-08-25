package dfgg.application.mining;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dfgg.application.embedding.ChampionItemEmbeddingTrainer;
import dfgg.application.embedding.WindowFactory;
import dfgg.domain.embedding.Embedding;
import dfgg.domain.embedding.EmbeddingEntityType;
import dfgg.domain.embedding.EmbeddingRepository;
import dfgg.domain.embedding.TrainingConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({EmbeddingTrainingBatchService.class, MatchParticipantWindowBuilder.class, WindowFactory.class, ChampionItemEmbeddingTrainer.class})
class EmbeddingTrainingBatchServiceTest {

    private static final double WIN_WEIGHT = 3.0;
    private static final String ALGORITHM_VERSION = "sgns-v1";

    @Autowired
    private EmbeddingTrainingBatchService embeddingTrainingBatchService;

    @Autowired
    private EmbeddingRepository embeddingRepository;

    @Test
    @DisplayName("실 매치 데이터를 읽어 공유 임베딩을 학습한다")
    @Sql("/sql/embedding-training-batch-service-test-data.sql")
    void trainFromMatchData_WhenGivenRealMatchData_LearnsSharedChampionAndItemEmbeddings() {
        // given: data.sql이 30개 매치(아군 챔피언 1~5번은 항상 승리해 3071 아이템을 구매하고,
        // 적군 챔피언 6~10번은 항상 패배해 3020 아이템을 구매하는 구성)와 태그가 있는 아이템 3071을 적재해둔다
        TrainingConfig config = new TrainingConfig(8, 4, 30, 0.05, 42L);

        // when
        EmbeddingTrainingOutcome outcome = embeddingTrainingBatchService.trainFromMatchData(
                WIN_WEIGHT, config, ALGORITHM_VERSION
        );
        Map<String, double[]> embeddings = outcome.embeddings();

        // then: 같은 팀에서 항상 함께 등장한 챔피언끼리, 적으로만 마주친 챔피언보다 더 가깝다
        double allyCloseness = cosineSimilarity(embeddings.get("1"), embeddings.get("2"));
        double enemyCloseness = cosineSimilarity(embeddings.get("1"), embeddings.get("6"));
        assertThat(allyCloseness).isGreaterThan(enemyCloseness);

        // then: 챔피언과 그 챔피언이 산 아이템도 임베딩 공간에 함께 학습된다
        assertThat(embeddings).containsKey("3071");

        // then: 학습 규모(매치 수, window 수)도 함께 보고된다
        assertThat(outcome.matchCount()).isEqualTo(30);
        assertThat(outcome.windowCount()).isGreaterThan(0);
    }

    @Test
    @DisplayName("학습이 끝나면 챔피언과 아이템 임베딩만 저장하고 태그 토큰은 저장하지 않는다")
    @Sql("/sql/embedding-training-batch-service-test-data.sql")
    void trainFromMatchData_WhenTrainingCompletes_PersistsChampionAndItemEmbeddingsOnly() {
        // given: data.sql이 챔피언 1~10번, 태그(Armor, Mana)를 가진 아이템 3071을 적재해둔다
        TrainingConfig config = new TrainingConfig(8, 4, 30, 0.05, 42L);

        // when
        embeddingTrainingBatchService.trainFromMatchData(WIN_WEIGHT, config, ALGORITHM_VERSION);

        // then: 챔피언 1~10번 + 아이템 3071만 저장되고, 태그 토큰("Armor", "Mana")은 저장되지 않는다
        List<Embedding> saved = embeddingRepository.findAll();
        assertThat(saved).hasSize(11);
        assertThat(saved).allSatisfy(embedding -> assertThat(embedding.getAlgorithmVersion()).isEqualTo(ALGORITHM_VERSION));

        Embedding championEmbedding = saved.stream()
                .filter(embedding -> embedding.getEntityType() == EmbeddingEntityType.CHAMPION
                        && embedding.getEntityId().equals(1L))
                .findFirst()
                .orElseThrow();
        assertThat(championEmbedding.getVector()).hasSize(8);

        Embedding itemEmbedding = saved.stream()
                .filter(embedding -> embedding.getEntityType() == EmbeddingEntityType.ITEM
                        && embedding.getEntityId().equals(3071L))
                .findFirst()
                .orElseThrow();
        assertThat(itemEmbedding.getVector()).hasSize(8);
    }

    @Test
    @DisplayName("같은 algorithmVersion으로 다시 실행하면 기존 임베딩을 중복 없이 교체한다")
    @Sql("/sql/embedding-training-batch-service-test-data.sql")
    void trainFromMatchData_WhenRunTwiceWithSameAlgorithmVersion_ReplacesPreviousEmbeddingsWithoutDuplicates() {
        // given
        TrainingConfig config = new TrainingConfig(8, 4, 30, 0.05, 42L);
        embeddingTrainingBatchService.trainFromMatchData(WIN_WEIGHT, config, ALGORITHM_VERSION);

        // when
        embeddingTrainingBatchService.trainFromMatchData(WIN_WEIGHT, config, ALGORITHM_VERSION);

        // then
        assertThat(embeddingRepository.findAll()).hasSize(11);
    }

    @Test
    @DisplayName("algorithmVersion이 비어 있으면 예외가 발생한다")
    @Sql("/sql/embedding-training-batch-service-test-data.sql")
    void trainFromMatchData_WhenAlgorithmVersionIsBlank_ThrowsIllegalArgumentException() {
        // given
        TrainingConfig config = new TrainingConfig(8, 4, 30, 0.05, 42L);

        // when & then
        assertThatThrownBy(() -> embeddingTrainingBatchService.trainFromMatchData(WIN_WEIGHT, config, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("카운터 임베딩은 상대했던 적 챔피언과 우리 팀 아이템 사이의 별도 임베딩 공간을 학습한다")
    @Sql("/sql/embedding-training-batch-service-test-data.sql")
    void trainCounterEmbeddingsFromMatchData_WhenGivenRealMatchData_LearnsEnemyChampionAndTeamItemEmbeddings() {
        // given: 아군(1~5)은 매번 승리해 3071을 사고, 그때 상대는 항상 6~10번이었다.
        //        즉 카운터 문맥에서 3071은 6~10번과는 매치마다 함께 등장하지만 1~5번과는 전혀 등장하지 않는다.
        TrainingConfig config = new TrainingConfig(8, 4, 30, 0.05, 42L);

        // when
        EmbeddingTrainingOutcome outcome = embeddingTrainingBatchService.trainCounterEmbeddingsFromMatchData(
                WIN_WEIGHT, config, "sgns-v1-counter"
        );
        Map<String, double[]> embeddings = outcome.embeddings();

        // then: 3071은 실제로 상대했던 6번과, 한 번도 상대한 적 없는 1번보다 더 가깝다
        double closeToActualOpponent = cosineSimilarity(embeddings.get("3071"), embeddings.get("6"));
        double closeToNeverOpposedChampion = cosineSimilarity(embeddings.get("3071"), embeddings.get("1"));
        assertThat(closeToActualOpponent).isGreaterThan(closeToNeverOpposedChampion);

        // then: 카운터 윈도우는 적 챔피언 한 명당 하나씩, 매치당 10개(양 팀 5명씩) 생성된다
        assertThat(outcome.matchCount()).isEqualTo(30);
        assertThat(outcome.windowCount()).isEqualTo(300);
    }

    @Test
    @DisplayName("카운터 임베딩은 식별(identity) 임베딩과 별개의 algorithmVersion으로 저장된다")
    @Sql("/sql/embedding-training-batch-service-test-data.sql")
    void trainCounterEmbeddingsFromMatchData_WhenTrainingCompletes_PersistsUnderGivenAlgorithmVersion() {
        // given
        TrainingConfig config = new TrainingConfig(8, 4, 30, 0.05, 42L);

        // when
        embeddingTrainingBatchService.trainCounterEmbeddingsFromMatchData(WIN_WEIGHT, config, "sgns-v1-counter");

        // then: 챔피언 1~10번 + 아이템 3071만 저장되고, 전부 요청한 algorithmVersion으로 태깅된다
        List<Embedding> saved = embeddingRepository.findAll();
        assertThat(saved).hasSize(11);
        assertThat(saved).allSatisfy(embedding -> assertThat(embedding.getAlgorithmVersion()).isEqualTo("sgns-v1-counter"));
    }

    @Test
    @DisplayName("카운터 임베딩 학습에서 algorithmVersion이 비어 있으면 예외가 발생한다")
    @Sql("/sql/embedding-training-batch-service-test-data.sql")
    void trainCounterEmbeddingsFromMatchData_WhenAlgorithmVersionIsBlank_ThrowsIllegalArgumentException() {
        // given
        TrainingConfig config = new TrainingConfig(8, 4, 30, 0.05, 42L);

        // when & then
        assertThatThrownBy(() -> embeddingTrainingBatchService.trainCounterEmbeddingsFromMatchData(WIN_WEIGHT, config, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private double cosineSimilarity(double[] a, double[] b) {
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
