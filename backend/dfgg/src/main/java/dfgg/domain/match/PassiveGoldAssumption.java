package dfgg.domain.match;

/**
 * 자연 골드가 특정 시각 이후 일정 속도로 들어온다는 실험 가정이다.
 * 지급 틱과 정수화가 확인되지 않았으므로 구간마다 uncertaintyGold를 차감한 최소값만 사용한다.
 * rateHundredthsPerSecond=204는 초당 2.04 Gold를 뜻한다.
 */
public record PassiveGoldAssumption(long activationMs, long rateHundredthsPerSecond, long uncertaintyGold) {

    public PassiveGoldAssumption {
        if (activationMs < 0 || rateHundredthsPerSecond < 0 || uncertaintyGold < 0) {
            throw new IllegalArgumentException("자연 골드 가정 값은 0 이상이어야 합니다.");
        }
    }

    /** 활성화 이후 구간에 대해 정수 Gold의 보수적 최소 지급량을 계산한다. */
    public long minimumBetween(long startMs, long endMs) {
        if (startMs < 0 || endMs < startMs) {
            throw new IllegalArgumentException("자연 골드 계산 구간의 시각이 올바르지 않습니다.");
        }
        long activeStart = Math.max(startMs, activationMs);
        long activeEnd = Math.max(endMs, activationMs);
        long activeMillis = activeEnd - activeStart;
        long accrued = Math.multiplyExact(activeMillis, rateHundredthsPerSecond) / 100_000;
        return Math.max(0, accrued - uncertaintyGold);
    }
}
