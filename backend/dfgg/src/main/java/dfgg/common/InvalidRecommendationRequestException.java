package dfgg.common;

/**
 * 형식은 맞지만 내용이 모순된 추천 요청 — e.g. 내 챔피언이 아군·적 목록에도 있다.
 */
public class InvalidRecommendationRequestException extends RuntimeException {
    public InvalidRecommendationRequestException(String message) {
        super(message);
    }
}
