package dfgg.application.match;

import java.util.List;

/**
 * 패치 하나의 구매 정규화 한 배치 결과.
 *
 * @param nextCursor 다음 호출에 넘길 커서. 실패한 경기가 있어도 진행한다.
 * @param hasMore    배치가 limit만큼 찼는지. 남았으면 같은 명령을 커서만 바꿔 반복한다.
 */
public record PurchaseNormalizationBatchResult(int processed, int succeeded, int failed, int savedPurchases,
                                               String nextCursor, boolean hasMore, List<String> failures) {

    public PurchaseNormalizationBatchResult {
        failures = List.copyOf(failures);
    }
}
