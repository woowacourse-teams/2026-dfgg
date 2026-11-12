package dfgg.common.exception;

/**
 * 저장된 증강 데이터가 없다. 관리자 수집이 한 번도 끝나지 않은 상태다.
 */
public class AugmentDataUnavailableException extends RuntimeException {
    public AugmentDataUnavailableException() {
        super("증강 데이터가 아직 없습니다.");
    }
}
