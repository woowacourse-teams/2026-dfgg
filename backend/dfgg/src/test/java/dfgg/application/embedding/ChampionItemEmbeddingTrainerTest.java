package dfgg.application.embedding;

import dfgg.domain.embedding.TrainingConfig;
import dfgg.domain.embedding.Window;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChampionItemEmbeddingTrainerTest {

    private final WindowFactory windowFactory = new WindowFactory();
    private final ChampionItemEmbeddingTrainer trainer = new ChampionItemEmbeddingTrainer();

    @Test
    @DisplayName("같은 윈도우에 자주 함께 등장한 토큰끼리 임베딩 공간에서 더 가깝다")
    void train_WhenTokensFrequentlyCoOccurInWindows_EmbeddingsAreCloser() {
        List<Window> windows = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            windows.add(new Window(List.of("Ahri", "Zed", "Leona", "Jinx", "Nautilus")));
            windows.add(new Window(List.of("Garen", "Darius", "Braum", "Ashe", "Sett")));
        }
        TrainingConfig config = new TrainingConfig(8, 4, 60, 0.05, 42L);

        Map<String, double[]> embeddings = trainer.train(windows, config);

        double ahriZed = cosineSimilarity(embeddings.get("Ahri"), embeddings.get("Zed"));
        double ahriGaren = cosineSimilarity(embeddings.get("Ahri"), embeddings.get("Garen"));
        assertThat(ahriZed).isGreaterThan(ahriGaren);
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
