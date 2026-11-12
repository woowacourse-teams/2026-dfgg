package dfgg.domain.score;

/**
 * 기본점수 = 50 + 150 × (0.6 × 개인 지표 A + 0.4 × 팀 기여 B − 0.5). 모드와 관계없이 같다.
 */
public final class BaseScore {

    private static final double INDIVIDUAL_WEIGHT = 0.6;
    private static final double TEAM_WEIGHT = 0.4;
    private static final double CENTER = 50;
    private static final double SPREAD = 150;
    private static final double NEUTRAL_PERFORMANCE = 0.5;

    private BaseScore() {
    }

    public static double of(double individualPerformance, double teamContribution) {
        double performance = INDIVIDUAL_WEIGHT * individualPerformance + TEAM_WEIGHT * teamContribution;
        return CENTER + SPREAD * (performance - NEUTRAL_PERFORMANCE);
    }
}
