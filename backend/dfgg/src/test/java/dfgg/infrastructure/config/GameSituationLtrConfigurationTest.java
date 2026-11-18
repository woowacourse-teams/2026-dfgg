package dfgg.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dfgg.application.recommend.v3.feature.FeatureExtractionPipeline;
import dfgg.application.recommend.v3.feature.FeatureName;
import dfgg.application.recommend.v3.feature.FeatureSchemaFingerprint;
import dfgg.application.recommend.v3.ranker.CandidateRanker;
import dfgg.application.recommend.v3.ranker.CoreRanking;
import dfgg.application.recommend.v3.ranker.GameSituationModelFixture;
import dfgg.application.recommend.v3.ranker.GameSituationRanking;
import dfgg.application.recommend.v3.ranker.TreeShapCalculator;
import dfgg.domain.champion.ChampionRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.DefaultResourceLoader;

/**
 * 게임 상황까지 읽는 새 모델은 서버에 없을 수 있다.
 *
 * <p>"없음"으로 치는 경우는 셋뿐이다(경로 없음, 빈 값, 파일 없음). 파일이 있는데 계약이 틀리면 뜨지 않는다.
 * 틀린 모델을 "없음"으로 넘기면 새 API가 지금 모델로 답하는 것을 아무도 모르게 된다.
 */
class GameSituationLtrConfigurationTest {

    private static final String STUMP =
            GameSituationModelFixture.stump(FeatureName.ENEMY_SPELL_DAMAGE_GOLD_SHARE, 0.5, 1.0, 2.0);

    @TempDir
    Path directory;

    private final GameSituationLtrConfiguration configuration = new GameSituationLtrConfiguration();
    private final DefaultResourceLoader resources = new DefaultResourceLoader();
    private final FeatureExtractionPipeline pipeline = mock(FeatureExtractionPipeline.class);
    private final CoreRanking baseline = new CoreRanking(mock(CandidateRanker.class), mock(TreeShapCalculator.class));

    private final Logger configurationLogger = (Logger) LoggerFactory.getLogger(GameSituationLtrConfiguration.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void attachLogAppender() {
        logs.start();
        configurationLogger.addAppender(logs);
    }

    @AfterEach
    void detachLogAppender() {
        configurationLogger.detachAppender(logs);
    }

    private List<ILoggingEvent> warnings() {
        return logs.list.stream().filter(event -> event.getLevel() == Level.WARN).toList();
    }

    @Test
    @DisplayName("경로가 비어 있으면 새 모델은 없음이고, 시작 로그에 경고를 한 번 남긴다")
    void gameSituationRanking_WhenPathBlank_AbsentWithWarning() {
        // when
        GameSituationRanking ranking = configuration.gameSituationRanking("", resources, pipeline);

        // then
        assertThat(ranking.isAvailable()).isFalse();
        assertThat(warnings()).hasSize(1);
    }

    @Test
    @DisplayName("경로가 가리키는 파일이 없으면 새 모델은 없음이고, 경고에 그 경로를 적는다 — 옛 묶음으로 되돌려도 서버가 뜬다")
    void gameSituationRanking_WhenFileMissing_AbsentWithWarningNamingThePath() {
        // given
        String modelPath = directory.resolve("missing.json").toUri().toString();

        // when
        GameSituationRanking ranking = configuration.gameSituationRanking(modelPath, resources, pipeline);

        // then
        assertThat(ranking.isAvailable()).isFalse();
        assertThat(warnings()).hasSize(1);
        assertThat(warnings().get(0).getFormattedMessage()).contains(modelPath);
    }

    @Test
    @DisplayName("전체 계약에 맞는 파일이 있으면 새 모델은 있음이고, 랭커의 모델 버전은 전체 지문으로 만든다")
    void gameSituationRanking_WhenFileMatchesFullContract_Available() {
        // given
        String modelPath = GameSituationModelFixture.write(directory, STUMP).toUri().toString();

        // when
        GameSituationRanking ranking = configuration.gameSituationRanking(modelPath, resources, pipeline);

        // then
        assertThat(ranking.isAvailable()).isTrue();
        assertThat(ranking.orElse(baseline).ranker().modelVersion())
                .isEqualTo("lambdamart-" + FeatureSchemaFingerprint.EXPECTED);
        assertThat(warnings()).isEmpty();
    }

    @Test
    @DisplayName("파일은 있는데 지문이 틀리면 없음으로 넘기지 않고 기동을 멈춘다")
    void gameSituationRanking_WhenFingerprintMismatches_Throws() throws IOException {
        // given
        Path file = GameSituationModelFixture.write(directory, STUMP);
        Files.writeString(file, Files.readString(file).replace(FeatureName.schemaFingerprint(), "deadbeefdeadbeef"));

        // when & then
        assertThatThrownBy(() -> configuration.gameSituationRanking(file.toUri().toString(), resources, pipeline))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("지문");
    }

    @Test
    @DisplayName("파일은 있는데 내용이 모델이 아니면 없음으로 넘기지 않고 기동을 멈춘다")
    void gameSituationRanking_WhenFileIsNotAModel_Throws() throws IOException {
        // given
        Path empty = Files.writeString(directory.resolve("empty.json"), "");
        Path broken = Files.writeString(directory.resolve("broken.json"), "{\"trees\": [");

        // when & then
        assertThatThrownBy(() -> configuration.gameSituationRanking(empty.toUri().toString(), resources, pipeline))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> configuration.gameSituationRanking(broken.toUri().toString(), resources, pipeline))
                .isInstanceOf(IllegalStateException.class);
        assertThat(warnings()).isEmpty();
    }

    @Test
    @DisplayName("지금 모델(기준 계약)을 새 모델 자리에 넣으면 기동을 멈춘다 — 상황 값을 읽지 않는 모델이 새 모델 행세를 하지 못한다")
    void gameSituationRanking_WhenBaselineModelGiven_Throws() {
        // when & then: 저장소의 씨앗 모델은 기준 계약이다
        assertThatThrownBy(() -> configuration.gameSituationRanking("ltr/model.json", resources, pipeline))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("새 모델이 있어도 타입만으로 받는 랭커는 지금 모델의 랭커 하나뿐이다")
    void context_WhenGameSituationModelAvailable_CandidateRankerBeanStaysSingle() {
        // given
        String modelPath = GameSituationModelFixture.write(directory, STUMP).toUri().toString();

        // when & then
        new ApplicationContextRunner()
                .withUserConfiguration(LtrModelConfiguration.class, GameSituationLtrConfiguration.class)
                .withBean(FeatureExtractionPipeline.class, () -> pipeline)
                .withBean(ChampionRepository.class, () -> mock(ChampionRepository.class))
                .withPropertyValues("recommendation.game-situation-ltr.model-path=" + modelPath)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(GameSituationRanking.class).isAvailable()).isTrue();
                    assertThat(context.getBean(CandidateRanker.class).modelVersion())
                            .isEqualTo("lambdamart-" + FeatureSchemaFingerprint.BASELINE);
                    assertThat(context.getBean(TreeShapCalculator.class)).isNotNull();
                });
    }

    @Test
    @DisplayName("GAME_SITUATION_LTR_MODEL_PATH를 주면 그 경로를 새 모델 경로로 쓴다")
    void modelPath_WhenEnvironmentVariableSet_UseIt() {
        // given
        String modelPath = directory.resolve("game-situation-model.json").toUri().toString();

        // when & then
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("GAME_SITUATION_LTR_MODEL_PATH=" + modelPath)
                .run(context -> assertThat(
                        context.getEnvironment().getProperty("recommendation.game-situation-ltr.model-path"))
                        .isEqualTo(modelPath));
    }

    @Test
    @DisplayName("GAME_SITUATION_LTR_MODEL_PATH가 없으면 새 모델 경로는 빈 값이다")
    void modelPath_WhenEnvironmentVariableMissing_IsBlank() {
        // when & then
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .run(context -> assertThat(
                        context.getEnvironment().getProperty("recommendation.game-situation-ltr.model-path"))
                        .isEmpty());
    }
}
