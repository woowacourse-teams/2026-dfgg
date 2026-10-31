package dfgg.presentation.dto.response;

import java.util.List;

/**
 * 아이템 추천 v2의 다중 빌드 응답이다.
 */
public record MultiBuildRecommendationResponse(
        String champion,
        String position,
        List<BuildOptionResponse> builds
) {
}
