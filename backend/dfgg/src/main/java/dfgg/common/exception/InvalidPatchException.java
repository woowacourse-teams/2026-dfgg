package dfgg.common.exception;

/**
 * 패치 값이 major.minor 형식이 아니다.
 */
public class InvalidPatchException extends RuntimeException {
    public InvalidPatchException(String patch, Throwable cause) {
        super("패치는 major.minor 형식이어야 합니다: " + patch, cause);
    }
}
