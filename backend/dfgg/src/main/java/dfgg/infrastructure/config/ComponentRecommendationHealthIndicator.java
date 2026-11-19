package dfgg.infrastructure.config;

import dfgg.application.recommend.component.ComponentRecommendationRuntime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/** 모델 경로를 줬는데 Component 추천이 준비되지 않았으면 readiness를 DOWN으로 만든다. */
@Component
public class ComponentRecommendationHealthIndicator implements HealthIndicator {
    private final ComponentRecommendationRuntime runtime;
    private final boolean configured;

    public ComponentRecommendationHealthIndicator(
            ComponentRecommendationRuntime runtime,
            @Value("${recommendation.component-ltr.model-path:}") String modelPath) {
        this.runtime = runtime;
        this.configured = !modelPath.isBlank();
    }

    @Override
    public Health health() {
        if (configured && !runtime.isAvailable()) {
            return Health.down().build();
        }
        return Health.up().build();
    }
}
