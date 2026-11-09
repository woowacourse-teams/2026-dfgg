package dfgg.presentation.dto.response;

import dfgg.presentation.dto.ItemDto;

import java.util.List;

public record RecommendationResponse(
        String champion,
        String position,
        List<ItemDto> items
) {
}
