package dfgg.presentation.dto.response;

import dfgg.presentation.dto.RecommendedItemV4Dto;
import java.util.List;

public record NextItemRecommendationV4Response(List<RecommendedItemV4Dto> recommendedItems) {
    public NextItemRecommendationV4Response {
        recommendedItems = List.copyOf(recommendedItems);
    }
}
