package dfgg.application.mining;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.application.embedding.ChampionItemEmbeddingTrainer;
import dfgg.application.embedding.WindowFactory;
import dfgg.domain.embedding.TrainingConfig;
import java.util.Map;
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

    @Autowired
    private EmbeddingTrainingBatchService embeddingTrainingBatchService;

    @Test
    @Sql("/sql/embedding-training-batch-service-test-data.sql")
    void 실_매치_데이터를_읽어_공유_임베딩을_학습한다() {
        // given: data.sql이 30개 매치(아군 챔피언 1~5번은 항상 승리해 3071 아이템을 구매하고,
        // 적군 챔피언 6~10번은 항상 패배해 3020 아이템을 구매하는 구성)와 태그가 있는 아이템 3071을 적재해둔다
        TrainingConfig config = new TrainingConfig(8, 4, 30, 0.05, 42L);

        // when
        Map<String, double[]> embeddings = embeddingTrainingBatchService.trainFromMatchData(WIN_WEIGHT, config);

        // then: 같은 팀에서 항상 함께 등장한 챔피언끼리, 적으로만 마주친 챔피언보다 더 가깝다
        double allyCloseness = cosineSimilarity(embeddings.get("1"), embeddings.get("2"));
        double enemyCloseness = cosineSimilarity(embeddings.get("1"), embeddings.get("6"));
        assertThat(allyCloseness).isGreaterThan(enemyCloseness);

        // then: 챔피언과 그 챔피언이 산 아이템도 임베딩 공간에 함께 학습된다
        assertThat(embeddings).containsKey("3071");
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
