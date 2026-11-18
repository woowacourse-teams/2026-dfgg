package dfgg.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.application.recommend.NextItemRecommendationService;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 서버에 게임 상황 모델이 없을 때. 새 API를 담은 서버 코드가 새 모델 묶음보다 먼저 배포되면 이 상태다.
 * 옛 묶음으로 되돌렸을 때도 같다.
 */
class RecommendationV5WithoutGameSituationModelPipelineTest extends RecommendationV5PipelineSupport {

    @Autowired
    private NextItemRecommendationService coreRecommendations;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    @DisplayName("새 모델이 없으면 새 API는 같은 요청의 v4와 같은 Core를 같은 순서로 추천한다")
    void recommendV5_WhenGameSituationModelAbsent_RecommendsSameCoresAsV4() throws Exception {
        // when
        List<Long> v4 = coreItemIds(V4, v4Body());
        List<Long> v5 = coreItemIds(V5, v5Body(0));

        // then
        assertThat(coreRecommendations.ranksWithGameSituationModel()).isFalse();
        assertThat(v4).isNotEmpty();
        assertThat(v5).isEqualTo(v4);
    }

    @Test
    @DisplayName("새 모델이 없으면 상태가 달라도 새 API의 추천은 바뀌지 않는다 — 지금 모델은 상황 값을 읽지 않는다")
    void recommendV5_WhenGameSituationModelAbsent_IgnoresReportedState() throws Exception {
        // when
        List<Long> whenEnemiesHaveNoKills = coreItemIds(V5, v5Body(0));
        List<Long> whenEnemiesHaveManyKills = coreItemIds(V5, v5Body(6));

        // then
        assertThat(whenEnemiesHaveManyKills).isEqualTo(whenEnemiesHaveNoKills);
    }

    @Test
    @DisplayName("새 모델이 없으면 새 API의 지표에 지금 모델 꼬리표가 붙는다")
    void recommendV5_WhenGameSituationModelAbsent_TagsMetersWithBaseline() throws Exception {
        // when
        coreItemIds(V5, v5Body(0));

        // then
        assertThat(meterRegistry.get("recommendation.v5.stage")
                .tag("stage", "total").tag("model", "baseline").timer().count()).isPositive();
        assertThat(meterRegistry.find("recommendation.v5.stage").tag("model", "game-situation").timer()).isNull();
    }
}
