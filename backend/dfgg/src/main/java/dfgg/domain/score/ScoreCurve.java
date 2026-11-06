package dfgg.domain.score;

/**
 * 실제 값을 기대값과 견줘 0~1로 바꾼다. {@code sigmoid(2·ln(q))}, 즉 {@code q² / (1 + q²)}이다.
 * 기대만큼이면 0.5, 두 배면 0.8, 절반이면 0.2다.
 */
public final class ScoreCurve {

    private ScoreCurve() {
    }

    /** 둘 다 1을 더해 비율을 만든다 — 포탑·에픽처럼 0이 흔한 항목에서 0으로 나누지 않는다. */
    public static double compare(double actual, double expected) {
        double ratio = (actual + 1) / (expected + 1);
        double squared = ratio * ratio;
        return squared / (1 + squared);
    }
}
