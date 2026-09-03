package dfgg.presentation.dto.response;

import dfgg.presentation.dto.RecommendedItemDto;
import java.util.List;

public record NextItemRecommendationResponse(
        List<RecommendedItemDto> recommendedItems,
        String servedBy
) {
}
