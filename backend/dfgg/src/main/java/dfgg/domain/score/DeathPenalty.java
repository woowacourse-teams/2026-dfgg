package dfgg.domain.score;

/**
 * 데스 페널티 = min(30, 5 × 허용 데스를 넘은 데스). 허용 데스 = 분당 허용 데스 × 게임 시간(분).
 */
public class DeathPenalty {

    private static final double PENALTY_PER_EXCESS_DEATH = 5;
    private static final double MAXIMUM_PENALTY = 30;

    private final double allowedDeathsPerMinute;

    public DeathPenalty(double allowedDeathsPerMinute) {
        this.allowedDeathsPerMinute = allowedDeathsPerMinute;
    }

    public double of(ScoringParticipant participant, GameDuration duration) {
        double excessDeaths = participant.excessDeaths(allowedDeathsPerMinute * duration.minutes());
        return Math.min(MAXIMUM_PENALTY, PENALTY_PER_EXCESS_DEATH * excessDeaths);
    }
}
