package dfgg.common.exception;

/**
 * LLM 응답을 쓸 수 없다. 호출하는 쪽은 이유를 null로 응답하고, {@link Reason}은 실패 사유 메트릭에 쓴다.
 */
public class LlmUnavailableException extends RuntimeException {

    public enum Reason {
        TIMEOUT,
        UNAVAILABLE,
        EMPTY
    }

    private final Reason reason;

    public LlmUnavailableException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public LlmUnavailableException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
