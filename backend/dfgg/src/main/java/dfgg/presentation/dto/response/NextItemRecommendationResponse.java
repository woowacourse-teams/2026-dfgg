package dfgg.presentation.dto.response;

import dfgg.presentation.dto.ItemDto;
import java.util.List;

public record NextItemRecommendationResponse(
        List<ItemDto> recommendedItems,
        String servedBy
) {
}
