package dfgg.application;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.application.embedding.ChampionItemEmbeddingTrainer;
import dfgg.domain.embedding.ContentContext;
import dfgg.domain.embedding.CounterContext;
import dfgg.domain.embedding.ParticipantBuild;
import dfgg.domain.embedding.TeamComposition;
import dfgg.domain.embedding.Window;
import dfgg.domain.embedding.TrainingConfig;

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

    @Test
    @DisplayName("참가자-빌드 문맥은 챔피언과 구매한 아이템들을 하나의 윈도우로 만든다")
    void participantBuildWindow_ReturnsChampionAndItemsAsOneWindow() {
        ParticipantBuild build = new ParticipantBuild("Ahri", List.of("RabadonsDeathcap", "VoidStaff"));

        Window window = trainer.participantBuildWindow(build);

        assertThat(window.tokens()).containsExactly("Ahri", "RabadonsDeathcap", "VoidStaff");
    }

    @Test
    @DisplayName("대응(카운터) 문맥은 마주한 적 챔피언 5명과 구매한 아이템들을 하나의 윈도우로 만든다")
    void counterContextWindow_ReturnsEnemyChampionsAndItemsAsOneWindow() {
        CounterContext counterContext = new CounterContext(
                List.of("Garen", "Darius", "Braum", "Ashe", "Sett"),
                List.of("FrozenHeart")
        );

        Window window = trainer.counterContextWindow(counterContext);

        assertThat(window.tokens()).containsExactly(
                "Garen", "Darius", "Braum", "Ashe", "Sett", "FrozenHeart"
        );
    }

    @Test
    @DisplayName("콘텐츠 문맥은 아이템과 그 아이템의 태그들을 하나의 윈도우로 만든다")
    void contentContextWindow_ReturnsItemAndTagsAsOneWindow() {
        ContentContext contentContext = new ContentContext(
                "FrozenHeart",
                List.of("Armor", "Mana", "Aura")
        );

        Window window = trainer.contentContextWindow(contentContext);

        assertThat(window.tokens()).containsExactly("FrozenHeart", "Armor", "Mana", "Aura");
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
