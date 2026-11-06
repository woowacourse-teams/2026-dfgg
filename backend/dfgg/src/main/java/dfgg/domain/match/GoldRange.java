package dfgg.domain.match;

import java.util.Optional;

/**
 * 과거 구매 직전 골드의 가능한 바깥 범위다. lower와 upper는 모두 골드 금액이다.
 * 프레임 스냅샷·거래 비용 구간·수입 가정으로 계산한 값이며 구매 순간에 직접
 * 관측한 지갑 잔액은 아니다. 따라서 폭이 좁다는 사실만으로 실제 골드 포함이 검증되지는 않는다.
 */
public record GoldRange(long lower, long upper) {

    public GoldRange {
        if (lower < 0 || upper < lower) {
            throw new IllegalArgumentException("골드 범위는 0 이상의 하한과 그 이상인 상한이 필요합니다.");
        }
    }

    /**
     * 구매 전후에 최소한 발생한 것으로 가정한 수입만 기존 범위에 반영한다.
     * 구매 전 최소 수입은 하한을 올리고, 구매 후 최소 수입은 구매 시점에 아직 없던
     * 골드이므로 상한에서 뺀다. beforeTransactionFloor는 시작 프레임 지갑과
     * 구매 전 아이템 거래의 최소 delta를 합친 값으로, 수입을 더하기 전의 바닥이다.
     * 두 최소 수입의 합이 가능한 구간 수입을 넘거나 새 범위가 비면 결과를 만들지 않는다.
     */
    public Optional<GoldRange> refine(long beforeTransactionFloor, long incomeUpper,
                                      long minimumBefore, long minimumAfter) {
        if (incomeUpper < 0 || minimumBefore < 0 || minimumAfter < 0) {
            throw new IllegalArgumentException("수입 상한과 구매 전후 최소 수입은 0 이상이어야 합니다.");
        }
        if (minimumBefore > incomeUpper || minimumAfter > incomeUpper - minimumBefore) {
            return Optional.empty();
        }
        long refinedLower = Math.max(lower, Math.max(0, beforeTransactionFloor + minimumBefore));
        long refinedUpper = upper - minimumAfter;
        if (refinedUpper < refinedLower) {
            return Optional.empty();
        }
        return Optional.of(new GoldRange(refinedLower, refinedUpper));
    }
}
