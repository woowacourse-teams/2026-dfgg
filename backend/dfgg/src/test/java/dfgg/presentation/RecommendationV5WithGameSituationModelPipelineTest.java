package dfgg.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.application.recommend.NextItemRecommendationService;
import dfgg.application.recommend.v3.feature.FeatureName;
import dfgg.application.recommend.v3.feature.FeatureSchemaFingerprint;
import dfgg.application.recommend.v3.ranker.CandidateRanker;
import dfgg.application.recommend.v3.ranker.GameSituationModelFixture;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 서버에 게임 상황 모델이 있을 때. 요청의 상태 값이 새 모델의 점수까지 가는지를 끝에서 끝으로 본다.
 *
 * <p>학습한 모델은 저장소에 없다. 테스트가 만든 작은 모델을 새 모델 자리에 넣는다.
 * 이 모델은 상대 킬 합이 10 이하면 전개 순위 1위 후보를, 넘으면 그 밖의 후보를 높게 친다.
 * 상대 킬만 다른 두 요청의 1순위가 갈리면 상태 값이 모델까지 간 것이다.
 */
class RecommendationV5WithGameSituationModelPipelineTest extends RecommendationV5PipelineSupport {

    private static final long DOMINIK = 3036L;

    /** 테스트가 만든 모델. 속성 공급자가 여러 번 불려도 파일은 하나다. JVM이 끝날 때 지운다. */
    private static final Path MODEL = writeModel();

    @Autowired
    private NextItemRecommendationService coreRecommendations;

    @Autowired
    private CandidateRanker candidateRanker;

    @Autowired
    private MeterRegistry meterRegistry;

    @DynamicPropertySource
    static void gameSituationModel(DynamicPropertyRegistry registry) {
        registry.add("recommendation.game-situation-ltr.model-path", () -> MODEL.toUri().toString());
    }

    private static Path writeModel() {
        try {
            Path directory = Files.createTempDirectory("game-situation-model");
            Path model = GameSituationModelFixture.write(directory, GameSituationModelFixture.interaction(
                    FeatureName.ENEMY_KILL_SUM, 10, FeatureName.BUILD_RANK, 1.5));
            model.toFile().deleteOnExit();
            directory.toFile().deleteOnExit();
            return model;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    @Test
    @DisplayName("새 모델이 있으면 상대의 킬만 다른 두 요청의 1순위가 갈린다 — 요청의 상태가 새 모델의 점수까지 간다")
    void recommendV5_WhenGameSituationModelAvailable_RanksByReportedState() throws Exception {
        // when
        List<Long> whenEnemiesHaveNoKills = coreItemIds(V5, v5Body(0));
        List<Long> whenEnemiesHaveManyKills = coreItemIds(V5, v5Body(6));

        // then: 표본에서 [몰락한 왕의 검, 무한의 대검] 다음으로 가장 많이 산 것이 도미닉 경의 인사다
        assertThat(coreRecommendations.ranksWithGameSituationModel()).isTrue();
        assertThat(whenEnemiesHaveNoKills.getFirst()).isEqualTo(DOMINIK);
        assertThat(whenEnemiesHaveManyKills.getFirst()).isNotEqualTo(DOMINIK);
        assertThat(whenEnemiesHaveManyKills).contains(DOMINIK);
    }

    @Test
    @DisplayName("새 모델이 있어도 v3·v4는 지금 모델로 순위를 매긴다")
    void recommendV3AndV4_WhenGameSituationModelAvailable_StayOnBaselineModel() throws Exception {
        // when
        List<Long> v3 = coreItemIds(V3, v3Body());
        List<Long> v4 = coreItemIds(V4, v4Body());
        List<Long> v5 = coreItemIds(V5, v5Body(0));

        // then: 타입만으로 받는 랭커는 지금 모델 것이고, v4의 Core는 v3와 같다. 새 모델이 매긴 v5와는 순서가 다르다
        assertThat(candidateRanker.modelVersion()).isEqualTo("lambdamart-" + FeatureSchemaFingerprint.BASELINE);
        assertThat(v4).isEqualTo(v3);
        assertThat(v5).isNotEqualTo(v4);
    }

    @Test
    @DisplayName("새 모델이 있으면 새 API의 지표에 새 모델 꼬리표가 붙고, v4를 불러도 새 API의 지표는 늘지 않는다")
    void recommendV5_WhenGameSituationModelAvailable_TagsMetersWithIt() throws Exception {
        // given
        coreItemIds(V5, v5Body(0));
        long v5Calls = v5TotalCount();

        // when
        coreItemIds(V4, v4Body());

        // then
        assertThat(v5Calls).isPositive();
        assertThat(v5TotalCount()).isEqualTo(v5Calls);
        assertThat(meterRegistry.find("recommendation.v5.stage").tag("model", "baseline").timer()).isNull();
    }

    private long v5TotalCount() {
        return meterRegistry.get("recommendation.v5.stage")
                .tag("stage", "total").tag("model", "game-situation").timer().count();
    }
}
