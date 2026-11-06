package dfgg.domain.score;

import java.util.Optional;

/**
 * 참가자 한 명의 점수와 배지. 배지는 팀 최고점에게만 있다.
 */
public class ParticipantScore {

    private final DfScore score;
    private final Badge badge;

    ParticipantScore(DfScore score, Badge badge) {
        this.score = score;
        this.badge = badge;
    }

    public DfScore score() {
        return score;
    }

    public Optional<Badge> badge() {
        return Optional.ofNullable(badge);
    }
}
