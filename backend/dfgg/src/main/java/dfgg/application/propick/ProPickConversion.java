package dfgg.application.propick;

import java.util.Optional;

/**
 * 한 행의 변환 결과. 바꿨으면 픽이, 건너뛰었으면 사유가 있다.
 */
public record ProPickConversion(
        Optional<ProPickDraft> draft,
        ProPickSkipReason skipReason
) {

    public static ProPickConversion converted(ProPickDraft draft) {
        return new ProPickConversion(Optional.of(draft), null);
    }

    public static ProPickConversion skipped(ProPickSkipReason reason) {
        return new ProPickConversion(Optional.empty(), reason);
    }
}
