package dfgg.application.recommend.v3.ranker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dfgg.application.recommend.v3.feature.FeatureName;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 학습한 모델은 저장소에 두지 않는다. 새 모델이 필요한 테스트는 이 도우미가 임시 폴더에 쓴 모델을 읽는다.
 * 도우미가 쓴 파일이 운영 로더로 읽히지 않으면 그 테스트들이 엉뚱한 이유로 실패하므로 여기서 먼저 확인한다.
 */
class GameSituationModelFixtureTest {

    @TempDir
    Path directory;

    @Test
    @DisplayName("도우미가 쓴 모델은 운영 로더가 전체 계약으로 읽고, 적어 준 트리대로 점수를 낸다")
    void write_WhenStumpGiven_IsLoadedByProductionLoader() throws IOException {
        // given: 상대 주문력 비중이 0.5 이하면 1.0, 넘으면 2.0
        Path file = GameSituationModelFixture.write(directory,
                GameSituationModelFixture.stump(FeatureName.ENEMY_SPELL_DAMAGE_GOLD_SHARE, 0.5, 1.0, 2.0));

        // when
        GradientBoostedTrees model;
        try (InputStream input = Files.newInputStream(file)) {
            model = LightGbmModelLoader.load(input);
        }

        // then
        assertThat(model.predict(featuresWith(FeatureName.ENEMY_SPELL_DAMAGE_GOLD_SHARE, 0.2))).isEqualTo(1.0);
        assertThat(model.predict(featuresWith(FeatureName.ENEMY_SPELL_DAMAGE_GOLD_SHARE, 0.8))).isEqualTo(2.0);
    }

    @Test
    @DisplayName("도우미가 쓴 모델은 기준 계약으로는 읽히지 않는다 — 지금 모델 자리에 잘못 넣으면 거부된다")
    void write_WhenLoadedAsBaseline_IsRejected() throws IOException {
        // given
        Path file = GameSituationModelFixture.write(directory,
                GameSituationModelFixture.stump(FeatureName.ENEMY_SPELL_DAMAGE_GOLD_SHARE, 0.5, 1.0, 2.0));

        // when & then
        try (InputStream input = Files.newInputStream(file)) {
            assertThatThrownBy(() -> LightGbmModelLoader.loadBaseline(input))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    @DisplayName("상황 값과 후보 값을 함께 보는 트리는 상황 값에 따라 높은 점수를 받는 후보가 뒤바뀐다")
    void interaction_WhenSituationChanges_FlipsWhichCandidateScoresHigher() throws IOException {
        // given: 상대 킬 합이 10 이하면 전개 순위 1.5 이하가, 넘으면 1.5 초과나 순위 없는 후보가 이긴다
        Path file = GameSituationModelFixture.write(directory, GameSituationModelFixture.interaction(
                FeatureName.ENEMY_KILL_SUM, 10, FeatureName.BUILD_RANK, 1.5));
        GradientBoostedTrees model;
        try (InputStream input = Files.newInputStream(file)) {
            model = LightGbmModelLoader.load(input);
        }

        // when & then
        assertThat(model.predict(features(0, 1))).isEqualTo(2.0);
        assertThat(model.predict(features(0, 2))).isEqualTo(1.0);
        assertThat(model.predict(features(30, 1))).isEqualTo(1.0);
        assertThat(model.predict(features(30, 2))).isEqualTo(2.0);
        assertThat(model.predict(features(30, Double.NaN))).isEqualTo(2.0);
        assertThat(model.predict(features(Double.NaN, 1))).isEqualTo(2.0);
    }

    private static double[] features(double enemyKillSum, double buildRank) {
        double[] features = new double[FeatureName.values().length];
        features[FeatureName.ENEMY_KILL_SUM.ordinal()] = enemyKillSum;
        features[FeatureName.BUILD_RANK.ordinal()] = buildRank;
        return features;
    }

    private static double[] featuresWith(FeatureName name, double value) {
        double[] features = new double[FeatureName.values().length];
        features[name.ordinal()] = value;
        return features;
    }
}
