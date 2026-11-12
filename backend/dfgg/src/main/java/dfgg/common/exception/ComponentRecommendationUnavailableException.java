package dfgg.common.exception;

public class ComponentRecommendationUnavailableException extends RuntimeException {
    public ComponentRecommendationUnavailableException() {
        super("현재 Component 추천을 사용할 수 없습니다.");
    }
}
