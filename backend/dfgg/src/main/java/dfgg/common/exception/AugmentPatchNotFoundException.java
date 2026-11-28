package dfgg.common.exception;

/**
 * 요청한 패치의 증강 데이터가 저장돼 있지 않다.
 */
public class AugmentPatchNotFoundException extends RuntimeException {
    public AugmentPatchNotFoundException(String patch) {
        super("저장된 증강 데이터가 없는 패치입니다: " + patch);
    }
}
