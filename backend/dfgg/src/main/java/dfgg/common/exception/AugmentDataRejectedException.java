package dfgg.common.exception;

/**
 * 외부 증강 데이터를 믿을 수 없어 받지 않는다. 직전 데이터로 계속 응답한다.
 */
public class AugmentDataRejectedException extends RuntimeException {
    public AugmentDataRejectedException(String message) {
        super(message);
    }

    public AugmentDataRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
