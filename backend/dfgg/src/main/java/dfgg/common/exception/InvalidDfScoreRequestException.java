package dfgg.common.exception;

/**
 * 형식은 맞지만 내용이 모순된 DF 점수 요청 — e.g. 한 팀에 같은 포지션이 둘이다.
 */
public class InvalidDfScoreRequestException extends RuntimeException {
    public InvalidDfScoreRequestException(String message) {
        super(message);
    }
}
