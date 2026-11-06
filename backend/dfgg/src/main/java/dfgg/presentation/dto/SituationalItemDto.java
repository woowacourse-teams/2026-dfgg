package dfgg.presentation.dto;

/**
 * 상대 조합 때문에 고려할 아이템. Core 목표와 무관하게 요청당 하나의 목록으로 낸다.
 * <p>
 * 추천 이유는 v3 추천 아이템과 같은 {@link RecommendationDescription}이다. 근거 적 챔피언은 counter에 담는다.
 */
public record SituationalItemDto(Long id, String name, String imageUrl, RecommendationDescription description,
                                 long additionalCost, boolean affordable) {
    public SituationalItemDto {
        if (id == null || id <= 0 || name == null || name.isBlank() || imageUrl == null || imageUrl.isBlank()
                || description == null || description.counter().isEmpty() || additionalCost < 0) {
            throw new IllegalArgumentException("상황 대응 아이템의 표시 정보·근거 챔피언·비용이 필요합니다.");
        }
    }
}
