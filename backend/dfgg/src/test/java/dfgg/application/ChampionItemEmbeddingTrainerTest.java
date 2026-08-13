package dfgg.application;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.application.ChampionItemEmbeddingTrainer.TeamComposition;
import dfgg.application.ChampionItemEmbeddingTrainer.TrainingConfig;
import dfgg.application.ChampionItemEmbeddingTrainer.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChampionItemEmbeddingTrainerTest {

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

    @Test
    @DisplayName("팀 구성 문맥은 아군 챔피언 윈도우와 적군 챔피언 윈도우를 각각 하나씩 만든다")
    void teamCompositionWindows_ReturnsOneWindowPerTeam() {
        TeamComposition allyTeam = new TeamComposition(List.of("Ahri", "Zed", "Leona", "Jinx", "Nautilus"));
        TeamComposition enemyTeam = new TeamComposition(List.of("Garen", "Darius", "Braum", "Ashe", "Sett"));

        List<Window> windows = trainer.teamCompositionWindows(allyTeam, enemyTeam);

        assertThat(windows).containsExactly(
                new Window(allyTeam.championTokens()),
                new Window(enemyTeam.championTokens())
        );
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
