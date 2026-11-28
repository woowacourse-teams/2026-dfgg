package dfgg.presentation.dto.response;

import dfgg.presentation.dto.RecommendedItemV4Dto;
import dfgg.presentation.dto.SituationalItemDto;
import java.util.List;

public record NextItemRecommendationV4Response(List<RecommendedItemV4Dto> recommendedItems,
                                               List<SituationalItemDto> situationalItems) {
    public NextItemRecommendationV4Response(List<RecommendedItemV4Dto> recommendedItems) {
        this(recommendedItems, List.of());
    }

    public NextItemRecommendationV4Response {
        recommendedItems = List.copyOf(recommendedItems);
        situationalItems = List.copyOf(situationalItems);
    }
}
