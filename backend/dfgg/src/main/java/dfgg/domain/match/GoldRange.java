package dfgg.domain.match;

/**
 * 과거 구매 직전 골드의 가능한 바깥 범위다. 프레임 골드와 거래 비용 구간,
 * 구간 수입이 음수가 아니라는 가정으로 계산하며 실제 구매 순간의 관측값은 아니다.
 */
public record GoldRange(long lower, long upper) {

    public GoldRange {
        if (lower < 0 || upper < lower) {
            throw new IllegalArgumentException("골드 범위는 0 이상의 하한과 그 이상인 상한이 필요합니다.");
        }
    }
}
