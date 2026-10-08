package dfgg.application.match;

import java.util.List;

/** 관리자 요청으로 저장된 원천 경기를 정규화한 결과다. */
public record PurchaseNormalizationResult(int processed, int succeeded, int failed,
                                          int savedPurchases, List<String> failures) {

    public PurchaseNormalizationResult {
        failures = List.copyOf(failures);
    }
}
