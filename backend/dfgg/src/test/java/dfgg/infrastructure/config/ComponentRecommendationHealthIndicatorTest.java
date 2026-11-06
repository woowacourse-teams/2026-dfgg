package dfgg.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dfgg.application.recommend.component.ComponentRecommendationRuntime;
import dfgg.application.recommend.component.ComponentRecommendationService;
import dfgg.infrastructure.external.dto.ItemResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

class ComponentRecommendationHealthIndicatorTest {
    private static final String MODEL_PATH = "file:/models/component/model.json";

    @Test
    @DisplayName("모델 경로가 있고 Component 추천이 준비되면 UP이다")
    void health_WhenConfiguredAndAvailable_Up() {
        // given
        var runtime = ComponentRecommendationRuntime.available(
                mock(ComponentRecommendationService.class), mock(ItemResponse.class));

        // when
        Health health = new ComponentRecommendationHealthIndicator(runtime, MODEL_PATH).health();

        // then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    @DisplayName("모델 경로가 있는데 Component 추천이 준비되지 않으면 DOWN이다")
    void health_WhenConfiguredButUnavailable_Down() {
        // when
        Health health = new ComponentRecommendationHealthIndicator(
                ComponentRecommendationRuntime.unavailable(), MODEL_PATH).health();

        // then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    @DisplayName("모델 경로가 없으면 Component 추천을 끈 것이므로 UP이다")
    void health_WhenNotConfigured_Up() {
        // when
        Health health = new ComponentRecommendationHealthIndicator(
                ComponentRecommendationRuntime.unavailable(), "").health();

        // then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    @DisplayName("DOWN이어도 모델 경로나 내부 오류를 응답에 담지 않는다")
    void health_WhenDown_HideDetails() {
        // when
        Health health = new ComponentRecommendationHealthIndicator(
                ComponentRecommendationRuntime.unavailable(), MODEL_PATH).health();

        // then
        assertThat(health.getDetails()).isEmpty();
    }
}
