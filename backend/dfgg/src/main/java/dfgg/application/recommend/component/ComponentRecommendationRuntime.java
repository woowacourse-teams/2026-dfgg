package dfgg.application.recommend.component;

import dfgg.infrastructure.external.dto.ItemResponse;
import java.util.List;
import java.util.Objects;

/** 기동 시 준비한 Component 추천과 카탈로그의 수명을 함께 관리한다. */
public final class ComponentRecommendationRuntime {
    private final ComponentRecommendationService recommendations;
    private final ItemResponse catalog;

    private ComponentRecommendationRuntime(ComponentRecommendationService recommendations, ItemResponse catalog) {
        this.recommendations = recommendations;
        this.catalog = catalog;
    }

    public static ComponentRecommendationRuntime available(ComponentRecommendationService recommendations,
                                                           ItemResponse catalog) {
        return new ComponentRecommendationRuntime(Objects.requireNonNull(recommendations), Objects.requireNonNull(catalog));
    }

    public static ComponentRecommendationRuntime unavailable() {
        return new ComponentRecommendationRuntime(null, null);
    }

    public boolean isAvailable() {
        return recommendations != null;
    }

    public ItemResponse catalog() {
        requireAvailable();
        return catalog;
    }

    public List<ComponentRecommendationService.TargetRecommendation> recommend(ComponentRecommendationQuery query) {
        requireAvailable();
        return recommendations.recommend(query, catalog);
    }

    private void requireAvailable() {
        if (!isAvailable()) {
            throw new IllegalStateException("Component 추천이 준비되지 않았습니다.");
        }
    }
}
