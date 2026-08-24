package dfgg.application.embedding;

import dfgg.domain.embedding.BuildContext;
import dfgg.domain.embedding.ContentContext;
import dfgg.domain.embedding.TrainingConfig;
import dfgg.domain.embedding.Window;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChampionItemEmbeddingTrainerTest {

    private final ChampionItemEmbeddingTrainer trainer = new ChampionItemEmbeddingTrainer();
    private final WindowFactory windowFactory = new WindowFactory();

    @Test
    @DisplayName("같은 윈도우에 자주 함께 등장한 챔피언끼리 임베딩 공간에서 더 가깝다")
    void train_WhenChampionsFrequentlyCoOccurInWindows_EmbeddingsAreCloser() {
        // given
        List<Window> windows = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            windows.add(new Window(List.of("Ahri", "Zed", "Leona", "Jinx", "Nautilus")));
            windows.add(new Window(List.of("Garen", "Darius", "Braum", "Ashe", "Sett")));
        }
        TrainingConfig config = new TrainingConfig(8, 4, 60, 0.05, 42L);

        // when
        Map<String, double[]> embeddings = trainer.train(windows, config);

        // then
        double ahriZed = cosineSimilarity(embeddings.get("Ahri"), embeddings.get("Zed"));
        double ahriGaren = cosineSimilarity(embeddings.get("Ahri"), embeddings.get("Garen"));
        assertThat(ahriZed).isGreaterThan(ahriGaren);
    }

    @Test
    @DisplayName("빌드 문맥으로 학습하면 챔피언과 그 챔피언이 자주 산 아이템이 임베딩 공간에서 더 가깝다")
    void train_WhenChampionFrequentlyBuysItem_ChampionAndItemEmbeddingsAreCloser() {
        // given
        List<Window> windows = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            windows.add(windowFactory.createBuildContextWindow(
                    new BuildContext("Ahri", List.of(), List.of(), List.of("RabadonsDeathcap", "VoidStaff"), true), 1.0
            ));
            windows.add(windowFactory.createBuildContextWindow(
                    new BuildContext("Garen", List.of(), List.of(), List.of("Thornmail"), false), 1.0
            ));
        }
        TrainingConfig config = new TrainingConfig(8, 4, 60, 0.05, 42L);

        // when
        Map<String, double[]> embeddings = trainer.train(windows, config);

        // then
        double ahriRabadons = cosineSimilarity(embeddings.get("Ahri"), embeddings.get("RabadonsDeathcap"));
        double ahriThornmail = cosineSimilarity(embeddings.get("Ahri"), embeddings.get("Thornmail"));
        assertThat(ahriRabadons).isGreaterThan(ahriThornmail);
    }

    @Test
    @DisplayName("빌드 문맥에 적 챔피언 정보가 섞여도, 아이템은 실제로 그 아이템을 산 챔피언과 자주 마주친 적 챔피언보다 더 가깝다")
    void train_WhenBuildContextIncludesEnemies_ItemStaysCloserToOwningChampionThanToFrequentEnemy() {
        // given: Ahri는 매번 RabadonsDeathcap을 사고, 매번 같은 적(Garen 등)과 마주친다
        List<Window> windows = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            windows.add(windowFactory.createBuildContextWindow(
                    new BuildContext(
                            "Ahri",
                            List.of(),
                            List.of("Garen", "Darius", "Braum", "Ashe", "Sett"),
                            List.of("RabadonsDeathcap", "VoidStaff"),
                            true
                    ), 1.0
            ));
        }
        TrainingConfig config = new TrainingConfig(8, 4, 60, 0.05, 42L);

        // when
        Map<String, double[]> embeddings = trainer.train(windows, config);

        // then: 아이템은 실제 구매자(Ahri)와 더 가깝고, 단순히 자주 마주친 적(Garen)과는 덜 가깝다
        double ahriRabadons = cosineSimilarity(embeddings.get("Ahri"), embeddings.get("RabadonsDeathcap"));
        double garenRabadons = cosineSimilarity(embeddings.get("Garen"), embeddings.get("RabadonsDeathcap"));
        assertThat(ahriRabadons).isGreaterThan(garenRabadons);
    }

    @Test
    @DisplayName("콘텐츠 문맥으로 학습하면 같은 Data Dragon 태그를 공유하는 아이템끼리 임베딩 공간에서 더 가깝다")
    void train_WhenItemsShareTags_ItemEmbeddingsAreCloser() {
        // given
        List<Window> windows = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            windows.add(windowFactory.createContentContextWindow(
                    new ContentContext("FrozenHeart", List.of("Armor", "Mana", "Aura"))
            ));
            windows.add(windowFactory.createContentContextWindow(
                    new ContentContext("Thornmail", List.of("Armor", "Health", "Aura"))
            ));
            windows.add(windowFactory.createContentContextWindow(
                    new ContentContext("InfinityEdge", List.of("AttackDamage", "CriticalStrike"))
            ));
        }
        TrainingConfig config = new TrainingConfig(8, 4, 60, 0.05, 42L);

        // when
        Map<String, double[]> embeddings = trainer.train(windows, config);

        // then
        double frozenHeartThornmail = cosineSimilarity(embeddings.get("FrozenHeart"), embeddings.get("Thornmail"));
        double frozenHeartInfinityEdge = cosineSimilarity(embeddings.get("FrozenHeart"), embeddings.get("InfinityEdge"));
        assertThat(frozenHeartThornmail).isGreaterThan(frozenHeartInfinityEdge);
    }

    @Test
    @DisplayName("같은 쌍이라도 윈도우 가중치가 높을수록 같은 학습 횟수 안에서 임베딩이 더 가까워진다")
    void train_WhenWindowWeightIsHigher_SamePairConvergesCloserWithinSameEpochs() {
        // given
        TrainingConfig config = new TrainingConfig(8, 4, 15, 0.05, 42L);

        List<Window> lowWeightWindows = new ArrayList<>();
        List<Window> highWeightWindows = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            lowWeightWindows.add(new Window(List.of("Ahri", "Thornmail"), 1.0));
            highWeightWindows.add(new Window(List.of("Ahri", "Thornmail"), 5.0));
        }

        // when
        Map<String, double[]> lowWeightEmbeddings = trainer.train(lowWeightWindows, config);
        Map<String, double[]> highWeightEmbeddings = trainer.train(highWeightWindows, config);

        // then
        double lowWeightSimilarity = cosineSimilarity(
                lowWeightEmbeddings.get("Ahri"), lowWeightEmbeddings.get("Thornmail"));
        double highWeightSimilarity = cosineSimilarity(
                highWeightEmbeddings.get("Ahri"), highWeightEmbeddings.get("Thornmail"));
        assertThat(highWeightSimilarity).isGreaterThan(lowWeightSimilarity);
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
