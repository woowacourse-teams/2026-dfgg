package dfgg.application.match;

import java.util.ArrayList;
import java.util.List;

/**
 * 구매 정규화 한 번의 실행에서 경기별 성공·실패를 기록하고 요약한다.
 * 모든 경기는 성공 또는 실패 중 하나로만 기록되므로 성공 수는 처리 수에서 실패 수를 빼서 구한다.
 */
class PurchaseNormalizationSummary {

    private final List<String> failures = new ArrayList<>();
    private int savedPurchases;

    void succeed(int savedPurchasesForMatch) {
        savedPurchases += savedPurchasesForMatch;
    }

    void fail(String matchId, String reason) {
        failures.add(matchId + ": " + reason);
    }

    PurchaseNormalizationResult toResult(int processed) {
        return new PurchaseNormalizationResult(processed, processed - failures.size(), failures.size(),
                savedPurchases, failures);
    }

    PurchaseNormalizationBatchResult toBatchResult(int processed, String nextCursor, boolean hasMore) {
        return new PurchaseNormalizationBatchResult(processed, processed - failures.size(), failures.size(),
                savedPurchases, nextCursor, hasMore, failures);
    }
}
