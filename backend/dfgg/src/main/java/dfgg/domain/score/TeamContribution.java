package dfgg.domain.score;

/**
 * 팀 기여 B(0~1) = 0.5 × KP + 0.5 × KDA ÷ (KDA + 3).
 */
public class TeamContribution {

    private static final double KDA_HALF_POINT = 3.0;

    private final double killParticipation;
    private final double kda;

    public TeamContribution(double killParticipation, double kda) {
        this.killParticipation = killParticipation;
        this.kda = kda;
    }

    public double score() {
        return 0.5 * killParticipation + 0.5 * kda / (kda + KDA_HALF_POINT);
    }

    /** MVP·ACE 동점 처리에 쓴다. */
    public double killParticipation() {
        return killParticipation;
    }
}
